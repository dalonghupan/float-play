package com.floatplay.ui

import com.floatplay.service.PlaybackService
import com.floatplay.settings.FloatPlaySettings
import java.awt.*
import java.awt.event.*
import javax.swing.*

class FloatPlayerWindow : JFrame() {

    private val videoPanel = JPanel()
    private val controlPanel = PlayerControlPanel()
    private val playbackService = PlaybackService()
    private val themeAdapter = ThemeAdapter()
    private val renderer = VideoRenderer(videoPanel)

    private var frameBuffer = ByteArray(1920 * 1080 * 3)
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
    }

    private fun setupUI() {
        layout = BorderLayout()
        videoPanel.preferredSize = Dimension(640, 360)
        videoPanel.background = Color.BLACK
        videoPanel.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                renderer.resize(videoPanel.width, videoPanel.height)
            }
        })

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
            if (playbackService.getPosition() > 0) {
                playbackService.pause()
                controlPanel.setPlayState(false)
            } else {
                playbackService.play()
                controlPanel.setPlayState(true)
            }
        }

        controlPanel.onStop = {
            playbackService.stop()
            controlPanel.setPlayState(false)
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

    fun openFile(path: String): Boolean {
        val success = playbackService.openFile(path)
        if (success) {
            title = "FloatPlay - ${path.substringAfterLast("/")}"
            startFrameUpdate()
        }
        return success
    }

    fun openUrl(url: String): Boolean {
        val success = playbackService.openUrl(url)
        if (success) {
            title = "FloatPlay - $url"
            startFrameUpdate()
        }
        return success
    }

    private fun startFrameUpdate() {
        updateTimer?.stop()
        updateTimer = Timer(33) { // ~30fps
            val width = videoPanel.width
            val height = videoPanel.height
            if (width > 0 && height > 0) {
                if (frameBuffer.size != width * height * 3) {
                    frameBuffer = ByteArray(width * height * 3)
                }
                if (playbackService.getFrame(frameBuffer, width, height)) {
                    renderer.updateFrame(frameBuffer, width, height)
                }
                controlPanel.updateProgress(
                    playbackService.getPosition(),
                    playbackService.getDuration()
                )
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

    private class VideoRenderer(private val videoPanel: JPanel) {
        private var currentImage: Image? = null
        private var panelWidth = 0
        private var panelHeight = 0

        fun resize(width: Int, height: Int) {
            panelWidth = width
            panelHeight = height
        }

        fun updateFrame(buffer: ByteArray, width: Int, height: Int) {
            val image = Toolkit.getDefaultToolkit().createImage(
                java.awt.image.MemoryImageSource(width, height, buffer.toIntArray(), 0, width)
            )
            currentImage = image
            videoPanel.repaint()
        }

        fun paint(g: Graphics2D) {
            currentImage?.let { img ->
                val imgWidth = img.getWidth(null)
                val imgHeight = img.getHeight(null)
                if (imgWidth > 0 && imgHeight > 0) {
                    val scale = minOf(
                        panelWidth.toDouble() / imgWidth,
                        panelHeight.toDouble() / imgHeight
                    )
                    val scaledWidth = (imgWidth * scale).toInt()
                    val scaledHeight = (imgHeight * scale).toInt()
                    val x = (panelWidth - scaledWidth) / 2
                    val y = (panelHeight - scaledHeight) / 2

                    g.drawImage(img, x, y, scaledWidth, scaledHeight, null)
                }
            }
        }

        private fun ByteArray.toIntArray(): IntArray {
            val intArray = IntArray(size / 3)
            for (i in intArray.indices) {
                val r = this[i * 3].toInt() and 0xFF
                val g = this[i * 3 + 1].toInt() and 0xFF
                val b = this[i * 3 + 2].toInt() and 0xFF
                intArray[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            return intArray
        }
    }
}
