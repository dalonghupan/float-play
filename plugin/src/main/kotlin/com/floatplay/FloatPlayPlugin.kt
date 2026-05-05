package com.floatplay

import com.floatplay.ui.FloatPlayerWindow
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project

class FloatPlayPlugin(private val project: Project) : ProjectComponent {

    private var playerWindow: FloatPlayerWindow? = null

    override fun projectOpened() {
        playerWindow = FloatPlayerWindow()
    }

    override fun projectClosed() {
        playerWindow?.dispose()
        playerWindow = null
    }

    fun getPlayerWindow(): FloatPlayerWindow? = playerWindow
}
