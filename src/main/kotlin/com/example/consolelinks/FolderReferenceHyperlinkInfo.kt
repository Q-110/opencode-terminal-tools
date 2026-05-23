package com.example.consolelinks

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

// 文件夹引用链接的点击处理器，负责在 Project 视图中定位目标文件夹。
internal class FolderReferenceHyperlinkInfo(
    private val project: Project,
    private val folder: VirtualFile
) : HyperlinkInfo {
    override fun navigate(project: Project) {
        ProjectView.getInstance(this.project).select(null, folder, true)
    }
}
