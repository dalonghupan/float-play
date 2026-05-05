use ffmpeg_next as ffmpeg;
use ffmpeg::format::{input, context::Input, dictionary::Dictionary};
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

fn is_network_url(path: &str) -> bool {
    path.starts_with("http://") || path.starts_with("https://")
        || path.starts_with("rtmp://") || path.starts_with("rtsp://")
}

impl VideoDecoder {
    pub fn new(input_path: &str) -> Result<Self, String> {
        let input_ctx = if is_network_url(input_path) {
            let opts = Dictionary::new()
                .set("timeout", "10000000")  // 10 second connect timeout (microseconds)
                .set("reconnect", "1")
                .set("reconnect_streamed", "1")
                .set("reconnect_delay_max", "5");
            ffmpeg::format::input_with_dictionary(&input_path, opts)
                .map_err(|e| format!("Failed to open input: {}", e))?
        } else {
            input(&input_path)
                .map_err(|e| format!("Failed to open input: {}", e))?
        };

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
            ffmpeg::format::Pixel::ARGB,
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
                    let mut argb_frame = VideoFrame::empty();
                    self.scaler.run(&decoded, &mut argb_frame)
                        .map_err(|e| format!("Failed to convert frame: {}", e))?;

                    let width = argb_frame.width();
                    let height = argb_frame.height();
                    let data = argb_frame.data(0).to_vec();

                    let pts = decoded.pts()
                        .map(|p| p as f64 * self.time_base)
                        .unwrap_or(0.0);

                    return Ok(DecodedFrame {
                        width,
                        height,
                        data,
                        pts,
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

    pub fn width(&self) -> u32 {
        self.decoder.width()
    }

    pub fn height(&self) -> u32 {
        self.decoder.height()
    }
}
