package io.github.q110.opencodeterminaltools

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

    private fun registerMenuFirst(actionManager: ActionManager, menuId: String, actionId: String) {
        val group = actionManager.getAction(menuId) as? DefaultActionGroup ?: return
        val action = actionManager.getAction(actionId) ?: return

        group.addAction(action, Constraints.FIRST)
    }
}
