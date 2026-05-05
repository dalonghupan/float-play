package com.floatplay.ui

import java.awt.FlowLayout
import javax.swing.*

class PlayerControlPanel : JPanel() {

    private val openFileBtn = JButton("打开文件")
    private val openUrlBtn = JButton("打开URL")
    private val playPauseBtn = JButton("▶")
    private val stopBtn = JButton("停止")
    private val progressBar = JSlider(0, 1000, 0)
    private val volumeSlider = JSlider(0, 100, 70)
    private val speedComboBox = JComboBox(arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"))

    var onOpenFile: (() -> Unit)? = null
    var onOpenUrl: (() -> Unit)? = null
    var onPlayPause: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onSeek: ((Long) -> Unit)? = null
    var onVolumeChange: ((Float) -> Unit)? = null
    var onSpeedChange: ((Float) -> Unit)? = null

    private var isDraggingProgress = false
    private var totalDuration: Long = 0

    init {
        layout = FlowLayout(FlowLayout.LEFT)

        openFileBtn.addActionListener {
            onOpenFile?.invoke()
        }

        openUrlBtn.addActionListener {
            onOpenUrl?.invoke()
        }

        playPauseBtn.addActionListener {
            onPlayPause?.invoke()
        }

        stopBtn.addActionListener {
            onStop?.invoke()
        }

        progressBar.addChangeListener {
            if (!isDraggingProgress && progressBar.valueIsAdjusting) {
                isDraggingProgress = true
            }
            if (isDraggingProgress && !progressBar.valueIsAdjusting) {
                isDraggingProgress = false
                val position = (progressBar.value.toLong() * totalDuration) / 1000
                onSeek?.invoke(position)
            }
        }

        volumeSlider.addChangeListener {
            if (!volumeSlider.valueIsAdjusting) {
                onVolumeChange?.invoke(volumeSlider.value / 100f)
            }
        }

        speedComboBox.addActionListener {
            val speedStr = (speedComboBox.selectedItem as String).replace("x", "")
            val speed = speedStr.toFloatOrNull() ?: 1.0f
            onSpeedChange?.invoke(speed)
        }

        add(openFileBtn)
        add(openUrlBtn)
        add(playPauseBtn)
        add(stopBtn)
        add(progressBar)
        add(JLabel("音量:"))
        add(volumeSlider)
        add(speedComboBox)
    }

    fun setPlayState(isPlaying: Boolean) {
        playPauseBtn.text = if (isPlaying) "⏸" else "▶"
    }

    fun updateProgress(current: Long, total: Long) {
        if (!isDraggingProgress && total > 0) {
            totalDuration = total
            progressBar.value = (current * 1000 / total).toInt()
        }
    }
}
