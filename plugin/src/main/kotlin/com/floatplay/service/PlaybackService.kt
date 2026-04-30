package com.floatplay.service

class PlaybackService {

    private var handle: Long = 0

    fun init() {
        handle = NativeBridge.nativeInit()
    }

    fun openFile(path: String): Boolean {
        return NativeBridge.nativeOpenFile(handle, path)
    }

    fun openUrl(url: String): Boolean {
        return NativeBridge.nativeOpenUrl(handle, url)
    }

    fun play() {
        NativeBridge.nativePlay(handle)
    }

    fun pause() {
        NativeBridge.nativePause(handle)
    }

    fun stop() {
        NativeBridge.nativeStop(handle)
    }

    fun seek(positionMs: Long) {
        NativeBridge.nativeSeek(handle, positionMs)
    }

    fun getPosition(): Long {
        return NativeBridge.nativeGetPosition(handle)
    }

    fun getDuration(): Long {
        return NativeBridge.nativeGetDuration(handle)
    }

    fun setVolume(volume: Float) {
        NativeBridge.nativeSetVolume(handle, volume)
    }

    fun getVolume(): Float {
        return NativeBridge.nativeGetVolume(handle)
    }

    fun setSpeed(speed: Float) {
        NativeBridge.nativeSetSpeed(handle, speed)
    }

    fun getFrame(buffer: ByteArray, width: Int, height: Int): Boolean {
        return NativeBridge.nativeGetFrame(handle, buffer, width, height)
    }

    fun dispose() {
        if (handle != 0L) {
            NativeBridge.nativeDestroy(handle)
            handle = 0
        }
    }
}
