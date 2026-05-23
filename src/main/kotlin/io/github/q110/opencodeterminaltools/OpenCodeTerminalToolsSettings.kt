package io.github.q110.opencodeterminaltools

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "OpenCodeTerminalToolsSettings",
    storages = [Storage("opencode-terminal-tools.xml")]
)
class OpenCodeTerminalToolsSettings : PersistentStateComponent<OpenCodeTerminalToolsSettings.StateData> {
    private var state = StateData()

    override fun getState(): StateData {
        return state
    }

    override fun loadState(state: StateData) {
        this.state = state
    }

    class StateData {
        var fileLinksEnabled: Boolean = true
        var copyLinksEnabled: Boolean = true
        var openCodeEditorOpenShortcut: String = "ctrl+x e"
    }

    companion object {
        fun getInstance(): OpenCodeTerminalToolsSettings {
            return ApplicationManager.getApplication().getService(OpenCodeTerminalToolsSettings::class.java)
        }
    }
}
