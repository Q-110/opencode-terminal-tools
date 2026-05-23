// 文件跳转链接处理器 — 点击后自动选择最佳文件并跳转到指定行
package io.github.q110.opencodeterminaltools.jump

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.q110.opencodeterminaltools.filter.displayPath
import io.github.q110.opencodeterminaltools.filter.normalizePath
import io.github.q110.opencodeterminaltools.filter.pathMatches

internal class FileReferenceHyperlinkInfo(
    private val project: Project,
    /** 同名文件候选列表（按显示路径排序） */
    private val files: List<VirtualFile>,
    private val fileName: String,
    /** 当前行中包含的路径上下文 */
    private val requestedPath: String?,
    private val hasLineNumber: Boolean,
    private val lineNumber: Int,
    /** 可选行范围终点 */
    private val endLineNumber: Int?,
    /** 最近路径缓存（按文件名索引） */
    private val recentFilePathsByName: Map<String, String>
) : HyperlinkInfo {
    /** 打开文件、选中行范围、滚动居中 */
    override fun navigate(project: Project) {
        val target = chooseBestFile() ?: chooseFile()
        if (target != null) {
            val descriptorLine = if (hasLineNumber) (lineNumber - 1).coerceAtLeast(0) else 0
            val editor = FileEditorManager.getInstance(this.project).openTextEditor(
                OpenFileDescriptor(this.project, target, descriptorLine, 0),
                true
            )

            if (editor != null) {
                val document = editor.document
                val startOffset: Int
                val endOffset: Int
                if (hasLineNumber) {
                    val startLine = (lineNumber - 1).coerceIn(0, document.lineCount - 1)
                    val endLine = ((endLineNumber ?: lineNumber) - 1).coerceIn(startLine, document.lineCount - 1)
                    startOffset = document.getLineStartOffset(startLine)
                    endOffset = document.getLineEndOffset(endLine)
                } else {
                    startOffset = 0
                    endOffset = document.textLength
                }

                editor.selectionModel.setSelection(startOffset, endOffset)
                editor.caretModel.moveToOffset(startOffset)
                editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            }
        }
    }

    /** 从候选文件中智能选择最佳文件 */
    private fun chooseBestFile(): VirtualFile? {
        if (files.size == 1) return files.first()

        // 优先按路径后缀精确匹配
        if (requestedPath != null) {
            findByPathSuffix(requestedPath)?.let { return it }
        }

        // 其次匹配最近出现过的路径
        val recentPath = recentFilePathsByName[fileName]
        if (recentPath != null) {
            findByPathSuffix(recentPath)?.let { return it }
        }

        // 最后按加权打分选择
        val scoredFiles = files.groupBy { scoreFile(it) }
        val bestScore = scoredFiles.keys.maxOrNull() ?: return null
        val bestFiles = scoredFiles[bestScore].orEmpty()
        return if (bestFiles.size == 1) bestFiles.first() else null
    }

    private fun findByPathSuffix(path: String): VirtualFile? {
        return files.firstOrNull {
            pathMatches(project, it, path)
        }
    }

    /** 打分规则：src/main +80, src/test -40, build/target/out -80, templates +30, static +20, 精确文件名后缀 +10 */
    private fun scoreFile(file: VirtualFile): Int {
        val path = normalizePath(displayPath(project, file))
        var score = 0
        if ("src/main/" in path) score += 80
        if ("src/test/" in path) score -= 40
        if ("target/" in path || "build/" in path || "out/" in path) score -= 80
        if ("src/main/resources/templates/" in path) score += 30
        if ("src/main/resources/static/" in path) score += 20
        if (path.endsWith("/$fileName")) score += 10
        return score
    }

    /** 自动选择失败时弹出文件选择对话框 */
    private fun chooseFile(): VirtualFile? {
        val dialog = FileChoiceDialog(project, files)
        return if (dialog.showAndGet()) dialog.selectedFile else null
    }
}
