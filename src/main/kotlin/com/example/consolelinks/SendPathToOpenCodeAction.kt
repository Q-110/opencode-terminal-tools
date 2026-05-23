package com.example.consolelinks

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile

class SendPathToOpenCodeAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val selectedFiles = selectedVirtualFiles(event)
        val virtualFile = selectedFiles.firstOrNull()
        val hasFile = selectedFiles.isNotEmpty()
        event.presentation.text = if (virtualFile?.isDirectory == true) {
            "Send Folder Path to OpenCode"
        } else {
            "Send File Path to OpenCode"
        }
        event.presentation.isEnabledAndVisible = project != null && hasFile
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val virtualFile = selectedVirtualFiles(event).firstOrNull()
        if (virtualFile == null) {
            OpenCodeBridgeService.notify(project, "没有找到要发送的文件或文件夹。", NotificationType.WARNING)
            return
        }

        val payload = "@${displayPath(project, virtualFile)}"
        when (
            val result = OpenCodeBridgeService.getInstance(project)
                .sendSelection(payload, event.dataContext, settleAtLineEnd = true)
        ) {
            is OpenCodeBridgeService.BridgeResult.Success -> {
                OpenCodeBridgeService.notify(project, "已发送到 OpenCode", NotificationType.INFORMATION)
            }
            is OpenCodeBridgeService.BridgeResult.Scheduled -> {
                // 新版 Terminal 需要等待工具窗口激活和焦点切换，最终结果由 service 回调通知。
            }
            is OpenCodeBridgeService.BridgeResult.Error -> {
                OpenCodeBridgeService.notify(project, result.message, NotificationType.WARNING)
            }
        }
    }

    private fun selectedVirtualFiles(event: AnActionEvent): List<VirtualFile> {
        val selectedFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (!selectedFiles.isNullOrEmpty()) {
            return selectedFiles.toList()
        }

        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        return if (virtualFile != null) listOf(virtualFile) else emptyList()
    }
}
