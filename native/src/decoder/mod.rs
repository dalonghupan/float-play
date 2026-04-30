pub mod video_decoder;
pub mod audio_decoder;

pub struct DecodedFrame {
    pub width: u32,
    pub height: u32,
    pub data: Vec<u8>,
}
