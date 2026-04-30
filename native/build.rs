fn main() {
    // Try pkg-config first
    if pkg_config::probe_library("libavcodec").is_err()
        && pkg_config::probe_library("libavformat").is_err()
        && pkg_config::probe_library("libavutil").is_err()
        && pkg_config::probe_library("libswscale").is_err()
        && pkg_config::probe_library("libswresample").is_err()
    {
        // Fallback: try common Homebrew paths on macOS
        if cfg!(target_os = "macos") {
            // Try Apple Silicon path first, then Intel
            let paths = ["/opt/homebrew/lib", "/usr/local/lib"];
            for path in &paths {
                if std::path::Path::new(path).exists() {
                    println!("cargo:rustc-link-search=native={}", path);
                }
            }
        }

        println!("cargo:rustc-link-lib=avcodec");
        println!("cargo:rustc-link-lib=avformat");
        println!("cargo:rustc-link-lib=avutil");
        println!("cargo:rustc-link-lib=swscale");
        println!("cargo:rustc-link-lib=swresample");
    }
}
