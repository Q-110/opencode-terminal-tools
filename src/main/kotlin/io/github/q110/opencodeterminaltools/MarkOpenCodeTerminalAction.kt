package io.github.q110.opencodeterminaltools

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class MarkOpenCodeTerminalAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabled = project != null &&
            OpenCodeBridgeService.getInstance(project).canFindTerminal(event.dataContext)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val marked = OpenCodeBridgeService.getInstance(project).markTerminal(event.dataContext)
        if (marked) {
            OpenCodeBridgeService.notify(project, "已将当前标签页标记为 OpenCode Terminal。", NotificationType.INFORMATION)
        } else {
            OpenCodeBridgeService.notify(project, "没有找到可写入的 Terminal 标签页。", NotificationType.WARNING)
        }
    }
}
