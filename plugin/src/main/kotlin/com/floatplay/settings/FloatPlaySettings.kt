package com.floatplay.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "FloatPlaySettings",
    storages = [Storage("floatplay.xml")]
)
class FloatPlaySettings : PersistentStateComponent<FloatPlaySettings.State> {

    data class State(
        var windowX: Int = 100,
        var windowY: Int = 100,
        var windowWidth: Int = 640,
        var windowHeight: Int = 360,
        var alwaysOnTop: Boolean = true,
        var volume: Int = 70,
        var speed: Float = 1.0f,
        var lastPlayedFile: String? = null,
        var lastPosition: Long = 0
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): FloatPlaySettings {
            return ApplicationManager.getApplication()
                .getService(FloatPlaySettings::class.java)
        }
    }
}
