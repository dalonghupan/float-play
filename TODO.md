# FloatPlay 待办事项

## 当前编译问题（需优先解决）

### 问题描述
`cargo check` 失败，原因是 FFmpeg 版本不匹配：
- `Cargo.toml` 中 `ffmpeg-sys-next = "7.1"` 要求 FFmpeg 7.x
- 通过 `brew install ffmpeg` 安装的是 FFmpeg 8.1.1
- `pkg-config` 检测到系统 FFmpeg 8.x，与 crate 要求的 7.x 不兼容

### 解决方案（二选一）
1. **升级 Rust crate**：将 `Cargo.toml` 中 `ffmpeg-next` 和 `ffmpeg-sys-next` 升级到 `"8.1"`，匹配系统 FFmpeg 8.x
2. **降级系统 FFmpeg**：`brew install ffmpeg@7` 或从源码编译 FFmpeg 7.x

### 其他注意事项
- 之前的构建尝试使用了 `features = ["build"]`（从源码编译 FFmpeg），需要 nasm。nasm 已通过 `brew install nasm` 安装。
- 如果选择从源码编译 FFmpeg，需在 `Cargo.toml` 中恢复 `ffmpeg-sys-next = { version = "7.1", features = ["build"] }`
- `native/target/` 目录可能有残留的旧构建缓存，建议 `cargo clean` 后重试

---

## 已完成（本次开发）

- [x] 插件框架搭建（ProjectComponent 生命周期）
- [x] JNI 桥接层（14 个 native 方法）
- [x] FFmpeg 视频解码器（解码 + RGB24 缩放）
- [x] FFmpeg 音频解码器（解码 + 重采样）
- [x] 悬浮窗口 UI（拖拽、缩放、置顶）
- [x] 播放控制面板（播放/暂停/停止/进度/音量/倍速）
- [x] 主题适配（深色/浅色）
- [x] 窗口设置持久化
- [x] 快捷键 Ctrl+Alt+P

### 本次新增完成项

- [x] **1. 解码线程实际实现** — `native/src/player/player_engine.rs`
  - `VideoDecoder` 移入解码线程，循环调用 `decode_next_frame()`
  - 通过 `mpsc::channel` 将解码帧传回主线程
  - `seek` 通过独立 channel 传递，视频线程内处理 seek 请求
  - PTS 基于帧间时间戳控制渲染节奏

- [x] **2. cpal 音频输出** — `native/src/audio/audio_output.rs`（新增模块）
  - 使用 cpal 默认输出设备，F32/Stereo/44100Hz
  - 音频解码线程直接推送到 AudioOutput 的共享缓冲区
  - cpal callback 从缓冲区拉取数据
  - 缓冲区限制 ~5 秒，防止内存溢出

- [x] **3. 音视频同步** — `native/src/player/player_engine.rs`
  - 视频帧 PTS 驱动渲染节奏：解码后根据 PTS 与实际经过时间差值 sleep
  - seek 时通过调整 `pts_offset` 保持帧节奏连续
  - 音频独立线程播放，不阻塞视频

- [x] **4. 网络流媒体完善（部分）** — `native/src/decoder/video_decoder.rs`
  - 检测网络 URL（http/https/rtmp/rtsp）
  - 使用 FFmpeg dictionary 设置：`timeout=10s`, `reconnect=1`, `reconnect_streamed=1`, `reconnect_delay_max=5`
  - **未完成**：audio_decoder.rs 中未添加相同网络选项

- [x] **5. 帧渲染性能优化** — Rust 侧输出 ARGB 格式
  - `video_decoder.rs` scaler 输出 `Pixel::ARGB` 而非 `RGB24`
  - Kotlin 侧使用 `BufferedImage` + `DataBufferInt` 直接操作像素数组
  - 消除了逐像素 RGB→ARGB 转换

- [x] **6. 帧缓存动态分配** — `plugin/.../FloatPlayerWindow.kt`
  - `allocateFrameBuffer()` 根据 `getVideoWidth()/Height()` 动态分配
  - 替代固定 1920x1080x3 的帧缓冲区

- [x] **7. 视频面板绘制重写** — `plugin/.../FloatPlayerWindow.kt`
  - 新建 `VideoPanel` 类继承 `JPanel`，override `paintComponent()`
  - 渲染时自动缩放适配面板尺寸，居中显示

- [x] **8. 播放状态判断修正** — `plugin/.../FloatPlayerWindow.kt`
  - `onPlayPause` 使用 `playbackService.isPlaying()` 替代 `getPosition() > 0`
  - JNI 新增 `nativeIsPlaying` 桥接方法

- [x] **9. 播放结束检测** — `plugin/.../FloatPlayerWindow.kt` + Rust
  - Rust 侧：解码到末尾时设置 `reached_end = true`，自动暂停
  - Kotlin 侧：Timer 中检测 `hasReachedEnd()`，自动停止并重置 UI
  - JNI 新增 `nativeHasReachedEnd` 桥接方法

- [x] **10. 错误处理增强** — `native/src/jni/bridge.rs` + `PlaybackService.kt`
  - `nativeOpenFile`/`nativeOpenUrl` 失败时 throw Java RuntimeException
  - `nativeSeek` 失败时 throw Java RuntimeException
  - Kotlin 侧 `openFile`/`openUrl`/`seek` 用 try-catch 捕获

- [x] **11. 资源释放保证** — 已验证完整调用链
  - `FloatPlayPlugin.projectClosed()` → `FloatPlayerWindow.dispose()` → `updateTimer.stop()` + `saveSettings()` + `playbackService.dispose()`
  - `PlaybackService.dispose()` → `NativeBridge.nativeDestroy(handle)` → Rust `Box::from_raw` → `PlayerEngine::drop()` → `stop_internal()` → join 所有线程

## 未完成

- [ ] **编译问题修复** — 见上方"当前编译问题"章节
- [ ] **audio_decoder.rs 网络选项** — 音频解码器也需要添加 FFmpeg dictionary 的 timeout/reconnect 参数（与 video_decoder.rs 一致）
- [ ] **播放速度对音频的实际影响验证** — 当前 `adjust_speed()` 通过跳帧实现变速，可能产生卡顿，需实际测试
- [ ] **线程安全验证** — `is_playing`/`volume`/`speed` 等 Arc<Mutex<>> 在高频读取场景下的性能需验证
- [ ] **实际运行测试** — 所有代码改动均未经过编译验证，需在解决编译问题后逐一测试
