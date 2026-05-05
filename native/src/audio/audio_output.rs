use std::sync::{Arc, Mutex};
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};

pub struct AudioOutput {
    _stream: cpal::Stream,
    buffer: Arc<Mutex<Vec<f32>>>,
}

impl AudioOutput {
    pub fn new() -> Result<Self, String> {
        let host = cpal::default_host();
        let device = host.default_output_device()
            .ok_or("No audio output device found")?;

        let config = device.default_output_config()
            .map_err(|e| format!("Failed to get output config: {}", e))?;

        let sample_rate = config.sample_rate().0;
        let channels = config.channels() as usize;

        let buffer: Arc<Mutex<Vec<f32>>> = Arc::new(Mutex::new(Vec::new()));
        let buffer_clone = buffer.clone();

        let stream = device.build_output_stream(
            &config.into(),
            move |data: &mut [f32], _: &cpal::OutputCallbackInfo| {
                let mut buf = buffer_clone.lock().unwrap();
                let available = buf.len().min(data.len());
                if available > 0 {
                    data[..available].copy_from_slice(&buf[..available]);
                    buf.drain(..available);
                    if available < data.len() {
                        data[available..].fill(0.0);
                    }
                } else {
                    data.fill(0.0);
                }
            },
            |err| eprintln!("Audio stream error: {}", err),
            None,
        ).map_err(|e| format!("Failed to build stream: {}", e))?;

        stream.play().map_err(|e| format!("Failed to start stream: {}", e))?;

        Ok(AudioOutput {
            _stream: stream,
            buffer,
        })
    }

    pub fn write_samples(&self, samples: &[f32]) {
        let mut buf = self.buffer.lock().unwrap();
        buf.extend_from_slice(samples);
        // Limit buffer size to prevent memory issues (~5 seconds at 44100Hz stereo)
        let max_samples = 44100 * 2 * 5;
        if buf.len() > max_samples {
            let excess = buf.len() - max_samples;
            buf.drain(..excess);
        }
    }
}
