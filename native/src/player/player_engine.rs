use std::sync::{Arc, Mutex, mpsc};
use std::thread;
use std::time::{Duration, Instant};

use crate::decoder::video_decoder::VideoDecoder;
use crate::decoder::audio_decoder::AudioDecoder;
use crate::decoder::DecodedFrame;
use crate::audio::audio_output::AudioOutput;

pub struct PlayerEngine {
    is_playing: Arc<Mutex<bool>>,
    exit_flag: Arc<Mutex<bool>>,
    current_position: Arc<Mutex<u64>>,
    duration: u64,
    volume: Arc<Mutex<f32>>,
    speed: Arc<Mutex<f32>>,
    latest_frame: Arc<Mutex<Option<DecodedFrame>>>,
    frame_rx: Option<mpsc::Receiver<DecodedFrame>>,
    seek_tx: Option<mpsc::Sender<u64>>,
    video_thread: Option<thread::JoinHandle<()>>,
    audio_thread: Option<thread::JoinHandle<()>>,
    video_width: Arc<Mutex<u32>>,
    video_height: Arc<Mutex<u32>>,
    reached_end: Arc<Mutex<bool>>,
    input_path: String,
}

impl PlayerEngine {
    pub fn new() -> Self {
        PlayerEngine {
            is_playing: Arc::new(Mutex::new(false)),
            exit_flag: Arc::new(Mutex::new(false)),
            current_position: Arc::new(Mutex::new(0)),
            duration: 0,
            volume: Arc::new(Mutex::new(1.0)),
            speed: Arc::new(Mutex::new(1.0)),
            latest_frame: Arc::new(Mutex::new(None)),
            frame_rx: None,
            seek_tx: None,
            video_thread: None,
            audio_thread: None,
            video_width: Arc::new(Mutex::new(0)),
            video_height: Arc::new(Mutex::new(0)),
            reached_end: Arc::new(Mutex::new(false)),
            input_path: String::new(),
        }
    }

    pub fn open_file(&mut self, path: &str) -> Result<(), String> {
        self.close_internal();

        let video = VideoDecoder::new(path)?;
        let audio = AudioDecoder::new(path)?;

        self.duration = video.get_duration();
        *self.video_width.lock().unwrap() = video.width();
        *self.video_height.lock().unwrap() = video.height();
        self.input_path = path.to_string();
        *self.exit_flag.lock().unwrap() = false;

        let (frame_tx, frame_rx) = mpsc::channel();
        let (seek_tx, seek_rx) = mpsc::channel::<u64>();

        self.frame_rx = Some(frame_rx);
        self.seek_tx = Some(seek_tx);
        *self.reached_end.lock().unwrap() = false;
        *self.current_position.lock().unwrap() = 0;

        // Video decode thread
        let is_playing = self.is_playing.clone();
        let exit_flag = self.exit_flag.clone();
        let current_position = self.current_position.clone();
        let latest_frame = self.latest_frame.clone();
        let reached_end = self.reached_end.clone();
        let input_path_v = self.input_path.clone();

        let video_handle = thread::spawn(move || {
            let mut video_decoder = video;
            let mut pts_offset: f64 = 0.0;
            let start_time = Instant::now();

            loop {
                if *exit_flag.lock().unwrap() {
                    break;
                }

                // Drain any pending seeks before decoding
                while let Ok(pos) = seek_rx.try_recv() {
                    let _ = video_decoder.seek(pos);
                    if pos == 0 {
                        pts_offset = 0.0;
                    } else {
                        let wall_elapsed = start_time.elapsed().as_secs_f64();
                        pts_offset = wall_elapsed - (pos as f64 / 1000.0);
                    }
                    *current_position.lock().unwrap() = pos;
                    *reached_end.lock().unwrap() = false;
                }

                if !*is_playing.lock().unwrap() {
                    // Paused — spin-wait, process seeks
                    while !*is_playing.lock().unwrap() {
                        if *exit_flag.lock().unwrap() {
                            return;
                        }
                        if let Ok(pos) = seek_rx.try_recv() {
                            if *reached_end.lock().unwrap() || pos == 0 {
                                match VideoDecoder::new(&input_path_v) {
                                    Ok(mut v) => {
                                        if pos > 0 {
                                            let _ = v.seek(pos);
                                        }
                                        video_decoder = v;
                                    }
                                    Err(_) => {
                                        thread::sleep(Duration::from_millis(100));
                                        continue;
                                    }
                                }
                            } else {
                                let _ = video_decoder.seek(pos);
                            }
                            if pos == 0 {
                                pts_offset = 0.0;
                            } else {
                                let wall_elapsed = start_time.elapsed().as_secs_f64();
                                pts_offset = wall_elapsed - (pos as f64 / 1000.0);
                            }
                            *current_position.lock().unwrap() = pos;
                            *reached_end.lock().unwrap() = false;
                            break;
                        }
                        thread::sleep(Duration::from_millis(10));
                    }
                    continue;
                }

                match video_decoder.decode_next_frame() {
                    Ok(frame) => {
                        let pts = frame.pts;
                        let target_time = pts + pts_offset;
                        let elapsed = start_time.elapsed().as_secs_f64();

                        if target_time > elapsed {
                            let sleep_dur = Duration::from_secs_f64(target_time - elapsed);
                            if sleep_dur < Duration::from_millis(200) {
                                thread::sleep(sleep_dur);
                            }
                        }

                        *current_position.lock().unwrap() = ((pts + pts_offset) * 1000.0) as u64;
                        *latest_frame.lock().unwrap() = Some(frame.clone());
                        let _ = frame_tx.send(frame);
                    }
                    Err(_) => {
                        *reached_end.lock().unwrap() = true;
                        *is_playing.lock().unwrap() = false;
                    }
                }
            }
        });

        // Audio decode thread
        let is_playing = self.is_playing.clone();
        let exit_flag = self.exit_flag.clone();
        let volume = self.volume.clone();
        let speed = self.speed.clone();
        let input_path_a = self.input_path.clone();

        let audio_handle = thread::spawn(move || {
            let mut audio_decoder = audio;
            let mut need_reopen = false;

            let audio_output = match AudioOutput::new() {
                Ok(output) => Some(output),
                Err(e) => {
                    eprintln!("Failed to init audio output: {}", e);
                    None
                }
            };

            loop {
                if *exit_flag.lock().unwrap() {
                    break;
                }

                if need_reopen || audio_decoder.ended {
                    while !*is_playing.lock().unwrap() {
                        if *exit_flag.lock().unwrap() {
                            return;
                        }
                        thread::sleep(Duration::from_millis(10));
                    }
                    match AudioDecoder::new(&input_path_a) {
                        Ok(d) => audio_decoder = d,
                        Err(_) => {
                            thread::sleep(Duration::from_millis(100));
                            continue;
                        }
                    }
                    need_reopen = false;
                }

                if !*is_playing.lock().unwrap() {
                    thread::sleep(Duration::from_millis(10));
                    continue;
                }

                match audio_decoder.decode_next_samples() {
                    Ok(samples) => {
                        let current_volume = *volume.lock().unwrap();
                        let current_speed = *speed.lock().unwrap();

                        let mut output: Vec<f32> = samples
                            .iter()
                            .map(|s| s * current_volume)
                            .collect();

                        if (current_speed - 1.0).abs() > 0.01 {
                            output = adjust_speed(&output, current_speed);
                        }

                        if let Some(ref ao) = audio_output {
                            ao.write_samples(&output);
                        }
                    }
                    Err(_) => {
                        need_reopen = true;
                    }
                }
            }
        });

        self.video_thread = Some(video_handle);
        self.audio_thread = Some(audio_handle);

        Ok(())
    }

    pub fn open_url(&mut self, url: &str) -> Result<(), String> {
        self.open_file(url)
    }

    pub fn play(&mut self) {
        *self.reached_end.lock().unwrap() = false;
        *self.is_playing.lock().unwrap() = true;
    }

    pub fn pause(&mut self) {
        *self.is_playing.lock().unwrap() = false;
    }

    pub fn stop(&mut self) {
        *self.is_playing.lock().unwrap() = false;
        *self.current_position.lock().unwrap() = 0;
        if let Some(ref tx) = self.seek_tx {
            let _ = tx.send(0);
        }
    }

    fn close_internal(&mut self) {
        // Signal threads to exit
        *self.exit_flag.lock().unwrap() = true;
        *self.is_playing.lock().unwrap() = false;

        // Drop channels
        self.frame_rx = None;
        self.seek_tx = None;

        // Join threads (they will exit quickly due to exit_flag)
        if let Some(handle) = self.video_thread.take() {
            let _ = handle.join();
        }
        if let Some(handle) = self.audio_thread.take() {
            let _ = handle.join();
        }

        // Reset state
        *self.current_position.lock().unwrap() = 0;
        *self.latest_frame.lock().unwrap() = None;
        *self.reached_end.lock().unwrap() = false;
    }

    pub fn seek(&mut self, position_ms: u64) -> Result<(), String> {
        if let Some(ref tx) = self.seek_tx {
            let _ = tx.send(position_ms);
            *self.current_position.lock().unwrap() = position_ms;
            *self.reached_end.lock().unwrap() = false;
        }
        Ok(())
    }

    pub fn get_frame(&mut self, buffer: &mut [u8], width: u32, height: u32) -> bool {
        let mut latest: Option<DecodedFrame> = None;
        if let Some(ref rx) = self.frame_rx {
            while let Ok(frame) = rx.try_recv() {
                latest = Some(frame);
            }
        }

        if let Some(frame) = latest {
            *self.latest_frame.lock().unwrap() = Some(frame.clone());
            if frame.width == width && frame.height == height {
                let copy_len = buffer.len().min(frame.data.len());
                buffer[..copy_len].copy_from_slice(&frame.data[..copy_len]);
                return true;
            }
        }

        let frame = self.latest_frame.lock().unwrap();
        if let Some(ref decoded) = *frame {
            if decoded.width == width && decoded.height == height {
                let copy_len = buffer.len().min(decoded.data.len());
                buffer[..copy_len].copy_from_slice(&decoded.data[..copy_len]);
                return true;
            }
        }
        false
    }

    pub fn set_volume(&mut self, volume: f32) {
        *self.volume.lock().unwrap() = volume.clamp(0.0, 1.0);
    }

    pub fn get_volume(&self) -> f32 {
        *self.volume.lock().unwrap()
    }

    pub fn set_speed(&mut self, speed: f32) {
        *self.speed.lock().unwrap() = speed.clamp(0.5, 2.0);
    }

    pub fn get_position(&self) -> u64 {
        *self.current_position.lock().unwrap()
    }

    pub fn get_duration(&self) -> u64 {
        self.duration
    }

    pub fn is_playing(&self) -> bool {
        *self.is_playing.lock().unwrap()
    }

    pub fn has_reached_end(&self) -> bool {
        *self.reached_end.lock().unwrap()
    }

    pub fn get_video_resolution(&self) -> (u32, u32) {
        let w = *self.video_width.lock().unwrap();
        let h = *self.video_height.lock().unwrap();
        (w, h)
    }
}

fn adjust_speed(samples: &[f32], speed: f32) -> Vec<f32> {
    let channels = 2;
    let frame_count = samples.len() / channels;
    let new_frame_count = (frame_count as f32 / speed) as usize;
    let mut output = Vec::with_capacity(new_frame_count * channels);

    for i in 0..new_frame_count {
        let src_pos = (i as f32 * speed) as usize;
        let src_idx = src_pos * channels;
        if src_idx + channels <= samples.len() {
            output.extend_from_slice(&samples[src_idx..src_idx + channels]);
        }
    }

    output
}

impl Drop for PlayerEngine {
    fn drop(&mut self) {
        self.close_internal();
    }
}
