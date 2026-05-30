// 文件夹跳转链接处理器 — 点击后在 Project View 中定位并展开文件夹
package io.github.q110.aiterminaltools.jump

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class FolderReferenceHyperlinkInfo(
    private val project: Project,
    private val folder: VirtualFile
) : HyperlinkInfo {
    override fun navigate(project: Project) {
        // 这里使用构造参数里的 project，确保跳转始终发生在创建链接时对应的项目里。
        ProjectView.getInstance(this.project).select(null, folder, true)
    }
}
