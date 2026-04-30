use ffmpeg_next as ffmpeg;
use ffmpeg::format::{input, context::Input};
use ffmpeg::codec::decoder::Video;
use ffmpeg::software::scaling::{Context as ScalingContext, flag::Flags};
use ffmpeg::media::Type;
use ffmpeg::util::frame::video::Video as VideoFrame;

use super::DecodedFrame;

pub struct VideoDecoder {
    input_ctx: Input,
    decoder: Video,
    scaler: ScalingContext,
    video_stream_index: usize,
    time_base: f64,
}

impl VideoDecoder {
    pub fn new(input_path: &str) -> Result<Self, String> {
        let input_ctx = input(&input_path)
            .map_err(|e| format!("Failed to open input: {}", e))?;

        let video_stream = input_ctx
            .streams()
            .best(Type::Video)
            .ok_or("No video stream found")?;

        let video_stream_index = video_stream.index();
        let time_base = video_stream.time_base();

        let context = ffmpeg::codec::context::Context::from_parameters(video_stream.parameters())
            .map_err(|e| format!("Failed to create codec context: {}", e))?;

        let decoder = context.decoder().video()
            .map_err(|e| format!("Failed to open video decoder: {}", e))?;

        let scaler = ScalingContext::get(
            decoder.format(),
            decoder.width(),
            decoder.height(),
            ffmpeg::format::Pixel::RGB24,
            decoder.width(),
            decoder.height(),
            Flags::BILINEAR,
        ).map_err(|e| format!("Failed to create scaler: {}", e))?;

        Ok(VideoDecoder {
            input_ctx,
            decoder,
            scaler,
            video_stream_index,
            time_base: f64::from(time_base),
        })
    }

    pub fn decode_next_frame(&mut self) -> Result<DecodedFrame, String> {
        for (stream, packet) in self.input_ctx.packets() {
            if stream.index() == self.video_stream_index {
                self.decoder.send_packet(&packet)
                    .map_err(|e| format!("Failed to send packet: {}", e))?;

                let mut decoded = VideoFrame::empty();
                while self.decoder.receive_frame(&mut decoded).is_ok() {
                    let mut rgb_frame = VideoFrame::empty();
                    self.scaler.run(&decoded, &mut rgb_frame)
                        .map_err(|e| format!("Failed to convert frame: {}", e))?;

                    let width = rgb_frame.width();
                    let height = rgb_frame.height();
                    let data = rgb_frame.data(0).to_vec();

                    return Ok(DecodedFrame {
                        width,
                        height,
                        data,
                    });
                }
            }
        }
        Err("End of stream".to_string())
    }

    pub fn seek(&mut self, position_ms: u64) -> Result<(), String> {
        let timestamp = (position_ms as f64 / 1000.0 / self.time_base) as i64;
        self.input_ctx.seek(timestamp, ..timestamp)
            .map_err(|e| format!("Failed to seek: {}", e))?;
        self.decoder.flush();
        Ok(())
    }

    pub fn get_duration(&self) -> u64 {
        let duration = self.input_ctx.duration();
        if duration < 0 {
            0
        } else {
            (duration as f64 / ffmpeg::ffi::AV_TIME_BASE as f64 * 1000.0) as u64
        }
    }
}
