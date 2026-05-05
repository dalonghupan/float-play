package com.floatplay.service

class PlaybackService {

    private var handle: Long = 0

    fun init() {
        handle = NativeBridge.nativeInit()
    }

    fun openFile(path: String): Boolean {
        return try {
            NativeBridge.nativeOpenFile(handle, path)
        } catch (e: RuntimeException) {
            false
        }
    }

    fun openUrl(url: String): Boolean {
        return try {
            NativeBridge.nativeOpenUrl(handle, url)
        } catch (e: RuntimeException) {
            false
        }
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
        try {
            NativeBridge.nativeSeek(handle, positionMs)
        } catch (e: RuntimeException) {
            // Seek error - silently ignored
        }
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

    fun isPlaying(): Boolean {
        return NativeBridge.nativeIsPlaying(handle)
    }

    fun hasReachedEnd(): Boolean {
        return NativeBridge.nativeHasReachedEnd(handle)
    }

    fun getVideoWidth(): Int {
        return NativeBridge.nativeGetVideoWidth(handle)
    }

    fun getVideoHeight(): Int {
        return NativeBridge.nativeGetVideoHeight(handle)
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
