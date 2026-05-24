package io.github.q110.opencodeterminaltools.bridge

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class StartOpenCodeTerminalAction : AnAction(AllIcons.Debugger.Console) {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        when (val result = OpenCodeBridgeService.getInstance(project).startOpenCodeTerminal()) {
            is OpenCodeBridgeService.BridgeResult.Success -> {
                OpenCodeBridgeService.notify(project, "Started OpenCode Terminal", NotificationType.INFORMATION)
            }
            is OpenCodeBridgeService.BridgeResult.Scheduled -> {
            }
            is OpenCodeBridgeService.BridgeResult.Error -> {
                OpenCodeBridgeService.notify(project, result.message, NotificationType.WARNING)
            }
        }
    }
}
