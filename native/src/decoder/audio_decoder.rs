use ffmpeg_next as ffmpeg;
use ffmpeg::format::{input, context::Input};
use ffmpeg::codec::decoder::Audio;
use ffmpeg::software::resampling::Context as ResamplingContext;
use ffmpeg::media::Type;

pub struct AudioDecoder {
    input_ctx: Input,
    decoder: Audio,
    resampler: ResamplingContext,
    audio_stream_index: usize,
}

impl AudioDecoder {
    pub fn new(input_path: &str) -> Result<Self, String> {
        let input_ctx = input(&input_path)
            .map_err(|e| format!("Failed to open input: {}", e))?;

        let audio_stream = input_ctx
            .streams()
            .best(Type::Audio)
            .ok_or("No audio stream found")?;

        let audio_stream_index = audio_stream.index();

        let context = ffmpeg::codec::context::Context::from_parameters(audio_stream.parameters())
            .map_err(|e| format!("Failed to create codec context: {}", e))?;

        let decoder = context.decoder().audio()
            .map_err(|e| format!("Failed to open audio decoder: {}", e))?;

        let resampler = ResamplingContext::get(
            decoder.format(),
            decoder.channel_layout(),
            decoder.rate(),
            ffmpeg::format::Sample::F32(ffmpeg::format::sample::Type::Planar),
            ffmpeg::channel_layout::ChannelLayout::STEREO,
            44100,
        ).map_err(|e| format!("Failed to create resampler: {}", e))?;

        Ok(AudioDecoder {
            input_ctx,
            decoder,
            resampler,
            audio_stream_index,
        })
    }

    pub fn decode_next_samples(&mut self) -> Result<Vec<f32>, String> {
        for (stream, packet) in self.input_ctx.packets() {
            if stream.index() == self.audio_stream_index {
                self.decoder.send_packet(&packet)
                    .map_err(|e| format!("Failed to send packet: {}", e))?;

                let mut decoded = ffmpeg::frame::Audio::empty();
                while self.decoder.receive_frame(&mut decoded).is_ok() {
                    let mut resampled = ffmpeg::frame::Audio::empty();
                    self.resampler.run(&decoded, &mut resampled)
                        .map_err(|e| format!("Failed to resample: {}", e))?;

                    let samples: Vec<f32> = resampled.data(0)
                        .chunks(4)
                        .map(|chunk| {
                            let mut bytes = [0u8; 4];
                            bytes.copy_from_slice(chunk);
                            f32::from_le_bytes(bytes)
                        })
                        .collect();

                    return Ok(samples);
                }
            }
        }
        Err("End of stream".to_string())
    }

    pub fn seek(&mut self, position_ms: u64) -> Result<(), String> {
        let time_base = self.input_ctx.stream(self.audio_stream_index)
            .map(|s| f64::from(s.time_base()))
            .unwrap_or(1.0 / 44100.0);
        let timestamp = (position_ms as f64 / 1000.0 / time_base) as i64;
        self.input_ctx.seek(timestamp, ..timestamp)
            .map_err(|e| format!("Failed to seek: {}", e))?;
        self.decoder.flush();
        Ok(())
    }
}
