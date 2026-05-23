package com.example.consolelinks

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager

class SendSelectionToOpenCodeAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        val project = event.project
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        event.presentation.isEnabled = project != null && hasSelection
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR)
        if (editor == null) {
            OpenCodeBridgeService.notify(project, "没有找到当前编辑器。", NotificationType.WARNING)
            return
        }

        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText
        if (selectedText.isNullOrEmpty()) {
            OpenCodeBridgeService.notify(project, "请先选中要发送给 OpenCode 的代码。", NotificationType.WARNING)
            return
        }

        val document = editor.document
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: FileDocumentManager.getInstance().getFile(document)
        if (virtualFile == null) {
            OpenCodeBridgeService.notify(project, "当前编辑器没有对应的文件。", NotificationType.WARNING)
            return
        }

        val startOffset = selectionModel.selectionStart
        val endOffset = selectionModel.selectionEnd
        val endOffsetForLine = (endOffset - 1).coerceAtLeast(startOffset)
        val startLine = document.getLineNumber(startOffset) + 1
        val endLine = document.getLineNumber(endOffsetForLine) + 1
        val filePath = displayPath(project, virtualFile)
        val payload = "$filePath:$startLine-$endLine\n$selectedText\n"

        when (val result = OpenCodeBridgeService.getInstance(project).sendSelection(payload, event.dataContext)) {
            is OpenCodeBridgeService.BridgeResult.Success -> {
                OpenCodeBridgeService.notify(
                    project,
                    "已聚焦 OpenCode Terminal，并调用 Enter（${result.enterMethod}）。EDITOR 脚本：${result.editorScript}",
                    NotificationType.INFORMATION
                )
            }
            is OpenCodeBridgeService.BridgeResult.Scheduled -> {
                // 新版 Terminal 需要等待工具窗口激活和焦点切换，最终结果由 service 回调通知。
            }
            is OpenCodeBridgeService.BridgeResult.Error -> {
                OpenCodeBridgeService.notify(project, result.message, NotificationType.WARNING)
            }
        }
    }
}
