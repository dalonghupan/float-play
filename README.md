# FloatPlay

轻量悬浮视频播放器，编码观影两不误。基于 Rust 高性能内核、专为 JetBrains IDEA 打造的悬浮视频播放插件。

## 技术栈

| 层级 | 技术选型 | 版本 | 说明 |
|------|----------|------|------|
| 应用层 | Kotlin | 1.9.25 | IDEA 插件开发、UI 绘制、窗口管理 |
| 解码层 | Rust + FFmpeg | Rust 1.94 / FFmpeg 7.1 | 音视频解码、流媒体解析 |
| 通信层 | JNI | 0.21 | Kotlin 与 Rust 跨语言调用 |
| 音频输出 | cpal | 0.15 | 跨平台音频输出 |
| UI 框架 | Swing | JDK 17 | IDEA 原生 UI 组件 |
| 构建工具 | Gradle + Cargo | Gradle 8.6 / Cargo 1.94 | Kotlin 用 Gradle，Rust 用 Cargo |
| IntelliJ SDK | IntelliJ Platform | 2023.3+ | IDEA 插件开发平台 |

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                    IDEA Plugin (Kotlin)                      │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ 窗口管理模块 │  │ UI 渲染模块  │  │ 插件生命周期管理    │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                    JNI / FFI Bridge                          │
├─────────────────────────────────────────────────────────────┤
│                    Rust Core Library                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ 视频解码引擎 │  │ 音频输出模块 │  │ 流媒体解析模块      │  │
│  │  (FFmpeg)    │  │   (cpal)    │  │                     │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## 项目结构

```
float-play/
├── plugin/                          # IDEA 插件模块 (Kotlin)
│   ├── build.gradle.kts
│   ├── src/
│   │   └── main/
│   │       ├── kotlin/
│   │       │   └── com/floatplay/
│   │       │       ├── FloatPlayPlugin.kt          # 插件入口
│   │       │       ├── ui/
│   │       │       │   ├── FloatPlayerWindow.kt    # 悬浮窗口
│   │       │       │   ├── PlayerControlPanel.kt   # 播放控制面板
│   │       │       │   └── ThemeAdapter.kt         # 主题适配
│   │       │       ├── service/
│   │       │       │   ├── PlaybackService.kt      # 播放服务
│   │       │       │   └── NativeBridge.kt         # JNI 桥接
│   │       │       ├── action/
│   │       │       │   ├── TogglePlayerAction.kt   # 显示/隐藏播放器
│   │       │       │   └── SettingsAction.kt       # 设置动作
│   │       │       └── settings/
│   │       │           └── FloatPlaySettings.kt    # 插件设置
│   │       └── resources/
│   │           └── META-INF/
│   │               └── plugin.xml                  # 插件配置
│
├── native/                          # Rust 原生库模块
│   ├── Cargo.toml
│   ├── src/
│   │   ├── lib.rs                   # 库入口
│   │   ├── decoder/
│   │   │   ├── mod.rs
│   │   │   ├── video_decoder.rs     # 视频解码器
│   │   │   └── audio_decoder.rs     # 音频解码器
│   │   ├── player/
│   │   │   ├── mod.rs
│   │   │   └── player_engine.rs     # 播放引擎
│   │   ├── stream/
│   │   │   ├── mod.rs
│   │   │   └── stream_parser.rs     # 流媒体解析
│   │   └── jni/
│   │       ├── mod.rs
│   │       └── bridge.rs            # JNI 导出函数
│   └── build.rs                     # 构建脚本
│
├── build.gradle.kts                 # 根项目构建脚本
├── settings.gradle.kts
└── README.md
```

## 环境要求

- JDK 17+
- Rust 1.70+
- FFmpeg 7.1+ (通过 ffmpeg-sys-next 自动编译)
- nasm (FFmpeg 编译依赖)
- macOS / Windows / Linux

## 构建指南

### 1. 构建 Rust 原生库

```bash
cd native
cargo build --release
```

构建产物：
- macOS: `target/release/libfloatplay_native.dylib`
- Windows: `target/release/floatplay_native.dll`
- Linux: `target/release/libfloatplay_native.so`

### 2. 构建 IDEA 插件

```bash
cd plugin
export JAVA_HOME=$(/usr/libexec/java_home -v 17)  # macOS
./gradlew buildPlugin
```

### 3. 开发模式运行

```bash
cd plugin
./gradlew runIde
```

## 本地调试

### 环境准备

```bash
# 1. 设置 JAVA_HOME (macOS)
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# 2. 验证 Rust 工具链
rustc --version   # >= 1.70
cargo --version

# 3. 安装 nasm (FFmpeg 编译依赖)
# macOS
brew install nasm
# 或手动下载: https://www.nasm.us/pub/nasm/releasebuilds/

# 4. 验证 nasm
nasm --version
```

### 调试步骤

#### Step 1: 编译 Rust 原生库

```bash
cd native

# 开发模式编译 (调试符号，编译更快)
cargo build

# 或发布模式编译 (优化后，用于性能测试)
cargo build --release
```

编译产物位置：
- macOS: `target/debug/libfloatplay_native.dylib` 或 `target/release/libfloatplay_native.dylib`
- Windows: `target/debug/floatplay_native.dll` 或 `target/release/floatplay_native.dll`
- Linux: `target/debug/libfloatplay_native.so` 或 `target/release/libfloatplay_native.so`

#### Step 2: 启动 IDEA 开发实例

```bash
cd plugin

# 启动带插件的 IDEA 开发实例
# Gradle 会自动设置 java.library.path 指向 native/target/release
# ./gradlew runIde
JAVA_HOME=$(/usr/libexec/java_home -v 17) /Users/yueting/software/gradle-8.6/bin/gradle runIde
```

启动后会打开一个新的 IDEA 实例，插件已自动加载。

#### Step 3: 测试插件功能

1. **打开播放器**: 菜单栏 `Tools` -> `Toggle FloatPlay` 或快捷键 `Ctrl + Alt + P`
2. **打开视频文件**: 在播放器窗口中选择本地视频文件
3. **测试控制**: 播放/暂停、进度拖拽、音量调节、倍速切换
4. **测试窗口**: 拖动移动、边缘拖拽调整大小、置顶切换

#### Step 4: 查看日志

```bash
# IDEA 日志位置
# macOS: ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log
# Windows: %USERPROFILE%\AppData\Local\JetBrains\IntelliJIdea*\log\idea.log
# Linux: ~/.cache/JetBrains/IntelliJIdea*/log/idea.log

# 实时查看日志
tail -f ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log | grep -i floatplay
```

### 常见问题排查

#### 1. UnsatisfiedLinkError: 找不到原生库

```
java.lang.UnsatisfiedLinkError: no floatplay_native in java.library.path
```

**解决方案**: 确保 Rust 库已编译，且 `runIde` 任务的 `java.library.path` 配置正确。

```bash
# 检查库文件是否存在
ls -la native/target/release/libfloatplay_native*

# 手动指定库路径运行
cd plugin
./gradlew runIde -Djava.library.path=../native/target/release
```

#### 2. FFmpeg 编译失败

```
nasm/yasm not found or too old
```

**解决方案**: 安装 nasm 并确保在 PATH 中。

```bash
# macOS
brew install nasm
# 或手动下载并复制到 /usr/local/bin/

# 验证
which nasm
nasm --version
```

#### 3. Gradle 下载失败

**解决方案**: 使用本地已安装的 Gradle。

```bash
# 检查本地 Gradle
ls ~/software/gradle-*/bin/gradle

# 使用本地 Gradle 运行
/Users/yueting/software/gradle-8.6/bin/gradle runIde
```

#### 4. JNI 函数找不到

```
java.lang.UnsatisfiedLinkError: ... returned NULL
```

**解决方案**: 检查 JNI 函数名是否与 Kotlin 包名完全匹配。

```
Kotlin: com.floatplay.service.NativeBridge.nativeInit
Rust:   Java_com_floatplay_service_NativeBridge_nativeInit
```

### 调试技巧

#### Rust 调试

```bash
# 启用详细日志
RUST_LOG=debug cargo build

# 查看 FFmpeg 编译过程
FFMPEG_BUILD_LOG=1 cargo build

# 运行 Rust 测试
cargo test
```

#### Kotlin 调试

在 IDEA 开发实例中：
1. 在代码中设置断点
2. 使用 `Debug` 模式运行 `runIde` 任务
3. 在开发实例中触发断点进行调试

```bash
# 以调试模式启动
./gradlew runIde --debug-jvm
```

#### JNI 调试

```bash
# 启用 JNI 检查
export JAVA_TOOL_OPTIONS="-Xcheck:jni"

# 运行插件
./gradlew runIde
```

## 快捷键

| 快捷键 | 功能 |
|--------|------|
| `Ctrl + Alt + P` | 显示/隐藏悬浮播放器 |

## 核心功能

- 本地视频文件播放 (MP4/MKV/AVI/FLV/WebM)
- 网络 URL 流媒体播放
- 播放/暂停/停止控制
- 进度条拖拽跳转
- 音量调节
- 倍速播放 (0.5x ~ 2.0x)
- 窗口自由拖动
- 窗口边缘拖拽调整大小
- 窗口置顶/取消置顶
- 深色/浅色主题自适应
- 窗口位置和大小持久化

## 许可证

MIT License
