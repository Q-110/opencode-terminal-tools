// 配置持久化层 — APP 级单例，存储到 opencode-terminal-tools.xml
package io.github.q110.opencodeterminaltools.settings

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

    /** 持久化字段定义，默认值均为开启 */
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
