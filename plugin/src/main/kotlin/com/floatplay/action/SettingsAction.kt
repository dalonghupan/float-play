package com.floatplay.action

import com.floatplay.settings.FloatPlaySettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import javax.swing.*

class SettingsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val settings = FloatPlaySettings.getInstance().state

        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val alwaysOnTopCheckBox = JCheckBox("窗口置顶", settings.alwaysOnTop)
        val volumeLabel = JLabel("默认音量: ${settings.volume}")
        val volumeSlider = JSlider(0, 100, settings.volume)

        panel.add(alwaysOnTopCheckBox)
        panel.add(volumeLabel)
        panel.add(volumeSlider)

        val result = JOptionPane.showConfirmDialog(
            null, panel, "FloatPlay 设置",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )

        if (result == JOptionPane.OK_OPTION) {
            settings.alwaysOnTop = alwaysOnTopCheckBox.isSelected
            settings.volume = volumeSlider.value
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }
}
