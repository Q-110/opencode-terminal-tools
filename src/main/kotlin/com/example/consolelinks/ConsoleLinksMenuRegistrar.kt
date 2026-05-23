package com.example.consolelinks

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.project.DumbAware

class ConsoleLinksMenuRegistrar : StartupActivity, DumbAware {
    override fun runActivity(project: com.intellij.openapi.project.Project) {
        val actionManager = ActionManager.getInstance()

        registerMenuFirst(actionManager, "EditorPopupMenu", "ConsoleLinks.SendSelectionToOpenCode")
        registerMenuFirst(actionManager, "ProjectViewPopupMenu", "ConsoleLinks.SendPathToOpenCode")
        registerMenuFirst(actionManager, "EditorTabPopupMenu", "ConsoleLinks.SendPathToOpenCode")
    }

    private fun registerMenuFirst(actionManager: ActionManager, menuId: String, actionId: String) {
        val group = actionManager.getAction(menuId) as? DefaultActionGroup ?: return
        val action = actionManager.getAction(actionId) ?: return

        group.addAction(action, Constraints.FIRST)
    }
}
