package com.floatplay.action

import com.floatplay.FloatPlayPlugin
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class TogglePlayerAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val plugin = project.getComponent(FloatPlayPlugin::class.java) ?: return
        val window = plugin.getPlayerWindow() ?: return

        if (window.isVisible) {
            window.isVisible = false
        } else {
            window.isVisible = true
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
