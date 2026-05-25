// 启动后动态注册右键菜单项 — 确保排在菜单最前面，不受加载顺序影响
package io.github.q110.aiterminaltools.bridge

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.components.service
import io.github.q110.aiterminaltools.console.AiConsoleErrorInlayService

class AiTerminalToolsMenuRegistrar : StartupActivity, DumbAware {
    override fun runActivity(project: com.intellij.openapi.project.Project) {
        project.service<AiConsoleErrorInlayService>().initialize()
        project.service<AiTerminalDropService>().initialize()

        val actionManager = ActionManager.getInstance()

        registerMenuFirst(actionManager, "EditorPopupMenu", "AiTerminalTools.SendSelectionToAiTerminal")
        registerMenuFirst(actionManager, "ProjectViewPopupMenu", "AiTerminalTools.SendPathToAiTerminal")
        registerMenuFirst(actionManager, "EditorTabPopupMenu", "AiTerminalTools.SendPathToAiTerminal")
        registerToolbarAction(actionManager, "AiTerminalTools.StartOpenCode")
        registerToolbarAction(actionManager, "AiTerminalTools.StartClaudeCode")
    }

    /** 以 Constraints.FIRST 插入菜单组最前面 */
    private fun registerMenuFirst(actionManager: ActionManager, menuId: String, actionId: String) {
        val group = actionManager.getAction(menuId) as? DefaultActionGroup ?: return
        val action = actionManager.getAction(actionId) ?: return

        if (group.getChildActionsOrStubs().any { it == action }) return
        group.addAction(action, Constraints.FIRST)
    }

    private fun registerToolbarAction(actionManager: ActionManager, actionId: String) {
        val action = actionManager.getAction(actionId) ?: return
        val group = toolbarGroup(actionManager) ?: return

        if (group.getChildActionsOrStubs().any { it == action }) return
        group.addAction(action, Constraints.LAST)
    }

    private fun toolbarGroup(actionManager: ActionManager): DefaultActionGroup? {
        return actionManager.getAction("MainToolbarRight") as? DefaultActionGroup
            ?: actionManager.getAction("MainToolBar") as? DefaultActionGroup
    }
}
