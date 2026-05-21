package com.example.opencodelinks

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager

internal class CopyTextHyperlinkInfo(
    private val project: Project,
    private val text: String
) : HyperlinkInfo {
    override fun navigate(project: Project) {
        CopyPasteManager.copyTextToClipboard(text)
        WindowManager.getInstance().getStatusBar(this.project)?.setInfo("已复制: ${shortStatusText(text)}")
    }
}
