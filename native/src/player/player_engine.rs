use std::sync::{Arc, Mutex};
use std::thread;

use crate::decoder::video_decoder::VideoDecoder;
use crate::decoder::audio_decoder::AudioDecoder;
use crate::decoder::DecodedFrame;

pub struct PlayerEngine {
    video_decoder: Option<VideoDecoder>,
    audio_decoder: Option<AudioDecoder>,
    is_playing: Arc<Mutex<bool>>,
    current_position: Arc<Mutex<u64>>,
    duration: u64,
    volume: f32,
    speed: f32,
    latest_frame: Arc<Mutex<Option<DecodedFrame>>>,
    decode_thread: Option<thread::JoinHandle<()>>,
}

impl PlayerEngine {
    pub fn new() -> Self {
        PlayerEngine {
            video_decoder: None,
            audio_decoder: None,
            is_playing: Arc::new(Mutex::new(false)),
            current_position: Arc::new(Mutex::new(0)),
            duration: 0,
            volume: 1.0,
            speed: 1.0,
            latest_frame: Arc::new(Mutex::new(None)),
            decode_thread: None,
        }
    }

    pub fn open_file(&mut self, path: &str) -> Result<(), String> {
        let video = VideoDecoder::new(path)?;
        let audio = AudioDecoder::new(path)?;

        self.duration = video.get_duration();
        self.video_decoder = Some(video);
        self.audio_decoder = Some(audio);

        Ok(())
    }

    pub fn open_url(&mut self, url: &str) -> Result<(), String> {
        self.open_file(url)
    }

    pub fn play(&mut self) {
        if self.video_decoder.is_none() {
            return;
        }

        let is_playing = self.is_playing.clone();
        let latest_frame = self.latest_frame.clone();
        let current_position = self.current_position.clone();

        *is_playing.lock().unwrap() = true;

        // Spawn decode thread for video frames
        let is_playing_clone = is_playing.clone();
        let _latest_frame = latest_frame.clone();
        let _current_position = current_position.clone();
        let handle = thread::spawn(move || {
            // Note: In a real implementation, we'd need to move the decoder into the thread
            // For now, this is a placeholder showing the threading model
            while *is_playing_clone.lock().unwrap() {
                thread::sleep(std::time::Duration::from_millis(33)); // ~30fps
            }
        });

        self.decode_thread = Some(handle);
    }

    pub fn pause(&mut self) {
        *self.is_playing.lock().unwrap() = false;
    }

    pub fn stop(&mut self) {
        *self.is_playing.lock().unwrap() = false;
        *self.current_position.lock().unwrap() = 0;
    }

    pub fn seek(&mut self, position_ms: u64) -> Result<(), String> {
        if let Some(ref mut video) = self.video_decoder {
            video.seek(position_ms)?;
        }
        if let Some(ref mut audio) = self.audio_decoder {
            audio.seek(position_ms)?;
        }
        *self.current_position.lock().unwrap() = position_ms;
        Ok(())
    }

    pub fn get_frame(&mut self, buffer: &mut [u8], width: u32, height: u32) -> bool {
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
        self.volume = volume.clamp(0.0, 1.0);
    }

    pub fn get_volume(&self) -> f32 {
        self.volume
    }

    pub fn set_speed(&mut self, speed: f32) {
        self.speed = speed.clamp(0.5, 2.0);
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
}

impl Drop for PlayerEngine {
    fn drop(&mut self) {
        *self.is_playing.lock().unwrap() = false;
        if let Some(handle) = self.decode_thread.take() {
            let _ = handle.join();
        }
    }
}
