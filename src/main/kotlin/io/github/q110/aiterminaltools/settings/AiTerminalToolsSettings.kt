// 配置持久化层 — APP 级单例，存储到 ai-terminal-tools.xml
package io.github.q110.aiterminaltools.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "AiTerminalToolsSettings",
    storages = [Storage("ai-terminal-tools.xml")]
)
class AiTerminalToolsSettings : PersistentStateComponent<AiTerminalToolsSettings.StateData> {
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
        var errorToAiTerminalIconsEnabled: Boolean = true
        var dragToAiTerminalEnabled: Boolean = true`n        var commitMessageModel: String = ""
    }

    companion object {
        fun getInstance(): AiTerminalToolsSettings {
            return ApplicationManager.getApplication().getService(AiTerminalToolsSettings::class.java)
        }
    }
}
