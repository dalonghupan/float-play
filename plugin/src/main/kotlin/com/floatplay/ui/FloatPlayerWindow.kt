package com.floatplay.ui

import com.floatplay.service.PlaybackService
import com.floatplay.settings.FloatPlaySettings
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import javax.swing.*
import kotlin.concurrent.thread

class FloatPlayerWindow : JFrame() {

    private val videoPanel = VideoPanel()
    private val controlPanel = PlayerControlPanel()
    private val playbackService = PlaybackService()
    private val themeAdapter = ThemeAdapter()

    private var frameBuffer = ByteArray(0)
    private var updateTimer: Timer? = null

    init {
        title = "FloatPlay"
        isAlwaysOnTop = true
        defaultCloseOperation = HIDE_ON_CLOSE

        playbackService.init()
        setupUI()
        setupDragListeners()
        setupResizeListeners()
        setupControlListeners()
        restoreSettings()

        // Stop playback when window is closed/hidden
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                updateTimer?.stop()
                playbackService.stop()
                controlPanel.setPlayState(false)
            }
        })
    }

    private fun setupUI() {
        layout = BorderLayout()
        videoPanel.preferredSize = Dimension(640, 360)
        videoPanel.background = Color.BLACK

        add(videoPanel, BorderLayout.CENTER)
        add(controlPanel, BorderLayout.SOUTH)

        themeAdapter.applyTheme(this)
        pack()
    }

    private fun setupDragListeners() {
        val dragListener = WindowDragListener(this)
        videoPanel.addMouseListener(dragListener)
        videoPanel.addMouseMotionListener(dragListener)
    }

    private fun setupResizeListeners() {
        val resizeListener = WindowResizeListener(this)
        addMouseListener(resizeListener)
        addMouseMotionListener(resizeListener)
    }

    private fun setupControlListeners() {
        controlPanel.onOpenFile = {
            val fileChooser = JFileChooser()
            fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
            fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "视频文件", "mp4", "mkv", "avi", "flv", "webm", "mov", "wmv"
            )
            val result = fileChooser.showOpenDialog(this)
            if (result == JFileChooser.APPROVE_OPTION) {
                val file = fileChooser.selectedFile
                openFile(file.absolutePath)
            }
        }

        controlPanel.onOpenUrl = {
            val url = JOptionPane.showInputDialog(
                this,
                "请输入视频URL:",
                "打开网络视频",
                JOptionPane.PLAIN_MESSAGE
            )
            if (!url.isNullOrBlank()) {
                openUrl(url)
            }
        }

        controlPanel.onPlayPause = {
            if (playbackService.isPlaying()) {
                playbackService.pause()
                controlPanel.setPlayState(false)
            } else {
                // If at end, seek to start first
                if (playbackService.hasReachedEnd()) {
                    playbackService.seek(0)
                }
                playbackService.play()
                controlPanel.setPlayState(true)
            }
        }

        controlPanel.onReplay = {
            playbackService.seek(0)
            playbackService.play()
            controlPanel.setPlayState(true)
        }

        controlPanel.onSeek = { positionMs ->
            playbackService.seek(positionMs)
        }

        controlPanel.onVolumeChange = { volume ->
            playbackService.setVolume(volume)
        }

        controlPanel.onSpeedChange = { speed ->
            playbackService.setSpeed(speed)
        }
    }

    fun openFile(path: String) {
        title = "FloatPlay - 打开中..."
        thread {
            val success = playbackService.openFile(path)
            SwingUtilities.invokeLater {
                if (success) {
                    title = "FloatPlay - ${path.substringAfterLast("/")}"
                    allocateFrameBuffer()
                    startFrameUpdate()
                } else {
                    title = "FloatPlay"
                    JOptionPane.showMessageDialog(this, "无法打开文件: $path", "错误", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }

    fun openUrl(url: String) {
        title = "FloatPlay - 连接中..."
        thread {
            val success = playbackService.openUrl(url)
            SwingUtilities.invokeLater {
                if (success) {
                    title = "FloatPlay - $url"
                    allocateFrameBuffer()
                    startFrameUpdate()
                } else {
                    title = "FloatPlay"
                    JOptionPane.showMessageDialog(this, "无法打开URL: $url\n\n请检查链接是否正确，以及FFmpeg是否支持该协议。", "错误", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }

    private fun allocateFrameBuffer() {
        val videoWidth = playbackService.getVideoWidth()
        val videoHeight = playbackService.getVideoHeight()
        if (videoWidth > 0 && videoHeight > 0) {
            frameBuffer = ByteArray(videoWidth * videoHeight * 4) // ARGB
        }
    }

    private fun startFrameUpdate() {
        updateTimer?.stop()
        val videoWidth = playbackService.getVideoWidth()
        val videoHeight = playbackService.getVideoHeight()
        updateTimer = Timer(33) { // ~30fps
            if (videoWidth > 0 && videoHeight > 0) {
                if (playbackService.getFrame(frameBuffer, videoWidth, videoHeight)) {
                    videoPanel.updateFrame(frameBuffer, videoWidth, videoHeight)
                }

                // 播放结束检测
                if (playbackService.hasReachedEnd()) {
                    controlPanel.setPlayState(false)
                    controlPanel.updateProgress(playbackService.getDuration(), playbackService.getDuration())
                } else {
                    controlPanel.updateProgress(
                        playbackService.getPosition(),
                        playbackService.getDuration()
                    )
                }
            }
        }
        updateTimer?.start()
    }

    fun toggleAlwaysOnTop() {
        isAlwaysOnTop = !isAlwaysOnTop
    }

    private fun restoreSettings() {
        val settings = FloatPlaySettings.getInstance().state
        location = Point(settings.windowX, settings.windowY)
        size = Dimension(settings.windowWidth, settings.windowHeight)
        isAlwaysOnTop = settings.alwaysOnTop
    }

    private fun saveSettings() {
        val settings = FloatPlaySettings.getInstance().state
        settings.windowX = x
        settings.windowY = y
        settings.windowWidth = width
        settings.windowHeight = height
        settings.alwaysOnTop = isAlwaysOnTop
    }

    override fun dispose() {
        updateTimer?.stop()
        saveSettings()
        playbackService.dispose()
        super.dispose()
    }

    private class WindowDragListener(private val window: JFrame) : MouseAdapter() {
        private var pressX = 0
        private var pressY = 0

        override fun mousePressed(e: MouseEvent) {
            pressX = e.xOnScreen - window.x
            pressY = e.yOnScreen - window.y
        }

        override fun mouseDragged(e: MouseEvent) {
            window.setLocation(
                e.xOnScreen - pressX,
                e.yOnScreen - pressY
            )
        }
    }

    private class WindowResizeListener(private val window: JFrame) : MouseAdapter() {
        private val BORDER_WIDTH = 8
        private var resizeEdge = ResizeEdge.NONE
        private var startX = 0
        private var startY = 0
        private var startWidth = 0
        private var startHeight = 0

        enum class ResizeEdge {
            NONE, LEFT, RIGHT, TOP, BOTTOM,
            TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
        }

        override fun mouseMoved(e: MouseEvent) {
            resizeEdge = detectEdge(e.x, e.y, window.width, window.height)
            window.cursor = when (resizeEdge) {
                ResizeEdge.LEFT, ResizeEdge.RIGHT -> Cursor(Cursor.W_RESIZE_CURSOR)
                ResizeEdge.TOP, ResizeEdge.BOTTOM -> Cursor(Cursor.N_RESIZE_CURSOR)
                ResizeEdge.TOP_LEFT, ResizeEdge.BOTTOM_RIGHT -> Cursor(Cursor.NW_RESIZE_CURSOR)
                ResizeEdge.TOP_RIGHT, ResizeEdge.BOTTOM_LEFT -> Cursor(Cursor.NE_RESIZE_CURSOR)
                else -> Cursor.getDefaultCursor()
            }
        }

        override fun mousePressed(e: MouseEvent) {
            startX = e.xOnScreen
            startY = e.yOnScreen
            startWidth = window.width
            startHeight = window.height
        }

        override fun mouseDragged(e: MouseEvent) {
            val dx = e.xOnScreen - startX
            val dy = e.yOnScreen - startY

            when (resizeEdge) {
                ResizeEdge.RIGHT -> {
                    window.size = Dimension(startWidth + dx, window.height)
                }
                ResizeEdge.BOTTOM -> {
                    window.size = Dimension(window.width, startHeight + dy)
                }
                ResizeEdge.LEFT -> {
                    window.setLocation(window.x + dx, window.y)
                    window.size = Dimension(startWidth - dx, window.height)
                }
                ResizeEdge.TOP -> {
                    window.setLocation(window.x, window.y + dy)
                    window.size = Dimension(window.width, startHeight - dy)
                }
                ResizeEdge.TOP_LEFT -> {
                    window.setLocation(window.x + dx, window.y + dy)
                    window.size = Dimension(startWidth - dx, startHeight - dy)
                }
                ResizeEdge.TOP_RIGHT -> {
                    window.setLocation(window.x, window.y + dy)
                    window.size = Dimension(startWidth + dx, startHeight - dy)
                }
                ResizeEdge.BOTTOM_LEFT -> {
                    window.setLocation(window.x + dx, window.y)
                    window.size = Dimension(startWidth - dx, startHeight + dy)
                }
                ResizeEdge.BOTTOM_RIGHT -> {
                    window.size = Dimension(startWidth + dx, startHeight + dy)
                }
                ResizeEdge.NONE -> {}
            }
        }

        private fun detectEdge(x: Int, y: Int, w: Int, h: Int): ResizeEdge {
            val left = x < BORDER_WIDTH
            val right = x > w - BORDER_WIDTH
            val top = y < BORDER_WIDTH
            val bottom = y > h - BORDER_WIDTH

            return when {
                top && left -> ResizeEdge.TOP_LEFT
                top && right -> ResizeEdge.TOP_RIGHT
                bottom && left -> ResizeEdge.BOTTOM_LEFT
                bottom && right -> ResizeEdge.BOTTOM_RIGHT
                left -> ResizeEdge.LEFT
                right -> ResizeEdge.RIGHT
                top -> ResizeEdge.TOP
                bottom -> ResizeEdge.BOTTOM
                else -> ResizeEdge.NONE
            }
        }
    }
}

private class VideoPanel : JPanel() {
    private var currentImage: Image? = null
    private var imgWidth = 0
    private var imgHeight = 0

    fun updateFrame(buffer: ByteArray, width: Int, height: Int) {
        // ARGB format - direct pixel copy using BufferedImage
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val dataBuffer = (image.raster.dataBuffer as DataBufferInt).data

        for (i in 0 until width * height) {
            val offset = i * 4
            val a = buffer[offset].toInt() and 0xFF
            val r = buffer[offset + 1].toInt() and 0xFF
            val g = buffer[offset + 2].toInt() and 0xFF
            val b = buffer[offset + 3].toInt() and 0xFF
            dataBuffer[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        currentImage = image
        imgWidth = width
        imgHeight = height
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        currentImage?.let { img ->
            if (imgWidth > 0 && imgHeight > 0) {
                val panelWidth = width
                val panelHeight = height
                val scale = minOf(
                    panelWidth.toDouble() / imgWidth,
                    panelHeight.toDouble() / imgHeight
                )
                val scaledWidth = (imgWidth * scale).toInt()
                val scaledHeight = (imgHeight * scale).toInt()
                val x = (panelWidth - scaledWidth) / 2
                val y = (panelHeight - scaledHeight) / 2

                (g as Graphics2D).drawImage(img, x, y, scaledWidth, scaledHeight, null)
            }
        }
    }
}
