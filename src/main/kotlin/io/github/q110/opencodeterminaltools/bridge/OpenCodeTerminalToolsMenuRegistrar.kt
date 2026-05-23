// 启动后动态注册右键菜单项 — 确保排在菜单最前面，不受加载顺序影响
package io.github.q110.opencodeterminaltools.bridge

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.project.DumbAware

class OpenCodeTerminalToolsMenuRegistrar : StartupActivity, DumbAware {
    override fun runActivity(project: com.intellij.openapi.project.Project) {
        val actionManager = ActionManager.getInstance()

        registerMenuFirst(actionManager, "EditorPopupMenu", "OpenCodeTerminalTools.SendSelectionToOpenCode")
        registerMenuFirst(actionManager, "ProjectViewPopupMenu", "OpenCodeTerminalTools.SendPathToOpenCode")
        registerMenuFirst(actionManager, "EditorTabPopupMenu", "OpenCodeTerminalTools.SendPathToOpenCode")
    }

    /** 以 Constraints.FIRST 插入菜单组最前面 */
    private fun registerMenuFirst(actionManager: ActionManager, menuId: String, actionId: String) {
        val group = actionManager.getAction(menuId) as? DefaultActionGroup ?: return
        val action = actionManager.getAction(actionId) ?: return

        group.addAction(action, Constraints.FIRST)
    }
}
