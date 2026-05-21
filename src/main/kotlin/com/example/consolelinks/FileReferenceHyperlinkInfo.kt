package com.example.consolelinks

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

// 文件引用链接的点击处理器，负责选择目标文件并跳转到指定位置。
internal class FileReferenceHyperlinkInfo(
    private val project: Project,
    private val files: List<VirtualFile>,
    private val fileName: String,
    private val requestedPath: String?,
    private val hasLineNumber: Boolean,
    private val lineNumber: Int,
    private val endLineNumber: Int?,
    private val recentFilePathsByName: Map<String, String>
) : HyperlinkInfo {
    override fun navigate(project: Project) {
        // 优先自动选择最佳文件，无法判断时再弹出选择框。
        val target = chooseBestFile() ?: chooseFile()
        if (target != null) {
            // IDEA 的行号从 0 开始，控制台里展示的行号从 1 开始，所以这里需要减 1。
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
                    // 行号范围会限制在文档实际行数内，避免越界。
                    val startLine = (lineNumber - 1).coerceIn(0, document.lineCount - 1)
                    val endLine = ((endLineNumber ?: lineNumber) - 1).coerceIn(startLine, document.lineCount - 1)
                    startOffset = document.getLineStartOffset(startLine)
                    endOffset = document.getLineEndOffset(endLine)
                } else {
                    startOffset = 0
                    endOffset = document.textLength
                }

                // 选中目标范围并把光标滚动到屏幕中间，便于用户立即看到跳转结果。
                editor.selectionModel.setSelection(startOffset, endOffset)
                editor.caretModel.moveToOffset(startOffset)
                editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            }
        }
    }

    private fun chooseBestFile(): VirtualFile? {
        // 只有一个候选文件时不需要任何消歧逻辑。
        if (files.size == 1) return files.first()

        if (requestedPath != null) {
            // 控制台本身带路径时，优先使用这个路径匹配。
            findByPathSuffix(requestedPath)?.let { return it }
        }

        val recentPath = recentFilePathsByName[fileName]
        if (recentPath != null) {
            // 如果之前出现过同名文件的完整路径，用最近路径辅助判断。
            findByPathSuffix(recentPath)?.let { return it }
        }

        // 对候选文件按常见项目结构打分，最高分唯一时自动选择。
        val scoredFiles = files.groupBy { scoreFile(it) }
        val bestScore = scoredFiles.keys.maxOrNull() ?: return null
        val bestFiles = scoredFiles[bestScore].orEmpty()
        return if (bestFiles.size == 1) bestFiles.first() else null
    }

    private fun findByPathSuffix(path: String): VirtualFile? {
        // 使用后缀匹配可以兼容绝对路径、相对路径和项目内显示路径。
        return files.firstOrNull {
            pathMatches(project, it, path)
        }
    }

    private fun scoreFile(file: VirtualFile): Int {
        // 分数体现常见代码位置偏好：主代码优先，构建产物和测试代码降低优先级。
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

    private fun chooseFile(): VirtualFile? {
        // 多个候选文件无法自动判断时，交给用户手动选择。
        val dialog = FileChoiceDialog(project, files)
        return if (dialog.showAndGet()) dialog.selectedFile else null
    }
}
