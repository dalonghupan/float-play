package com.floatplay.ui

import java.awt.Color
import javax.swing.UIManager

class ThemeAdapter {

    fun isDarkTheme(): Boolean {
        val laf = UIManager.getLookAndFeel()
        return laf.name.contains("Dark") ||
            UIManager.getColor("Panel.background")?.let {
                it.red + it.green + it.blue < 384
            } ?: false
    }

    fun applyTheme(window: FloatPlayerWindow) {
        if (isDarkTheme()) {
            applyDarkTheme(window)
        } else {
            applyLightTheme(window)
        }
    }

    private fun applyDarkTheme(window: FloatPlayerWindow) {
        window.contentPane.background = Color(43, 43, 43)
    }

    private fun applyLightTheme(window: FloatPlayerWindow) {
        window.contentPane.background = Color(242, 242, 242)
    }
}
