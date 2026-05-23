package com.example.consolelinks

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

// 保存插件全局设置，让已有和新打开的项目都使用同一份 Console Links 配置。
@Service(Service.Level.APP)
@State(
    name = "ConsoleLinksSettings",
    storages = [Storage("console-links.xml")]
)
class ConsoleLinksSettings : PersistentStateComponent<ConsoleLinksSettings.StateData> {
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
        fun getInstance(): ConsoleLinksSettings {
            return ApplicationManager.getApplication().getService(ConsoleLinksSettings::class.java)
        }
    }
}
