package com.floatplay.service

object NativeBridge {

    init {
        System.loadLibrary("floatplay_native")
    }

    // 初始化播放器引擎
    external fun nativeInit(): Long

    // 打开本地文件
    external fun nativeOpenFile(handle: Long, filePath: String): Boolean

    // 打开网络流
    external fun nativeOpenUrl(handle: Long, url: String): Boolean

    // 播放控制
    external fun nativePlay(handle: Long)
    external fun nativePause(handle: Long)
    external fun nativeStop(handle: Long)

    // 进度控制
    external fun nativeSeek(handle: Long, positionMs: Long)
    external fun nativeGetPosition(handle: Long): Long
    external fun nativeGetDuration(handle: Long): Long

    // 音量控制
    external fun nativeSetVolume(handle: Long, volume: Float)
    external fun nativeGetVolume(handle: Long): Float

    // 倍速控制
    external fun nativeSetSpeed(handle: Long, speed: Float)

    // 获取视频帧 (用于渲染到 Swing 组件)
    external fun nativeGetFrame(handle: Long, buffer: ByteArray, width: Int, height: Int): Boolean

    // 释放资源
    external fun nativeDestroy(handle: Long)
}
