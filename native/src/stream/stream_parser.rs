pub enum SourceType {
    LocalFile,
    NetworkStream,
}

pub struct StreamParser;

impl StreamParser {
    pub fn parse_source(source: &str) -> SourceType {
        if source.starts_with("http://") || source.starts_with("https://") || source.starts_with("rtmp://") {
            SourceType::NetworkStream
        } else {
            SourceType::LocalFile
        }
    }
}
