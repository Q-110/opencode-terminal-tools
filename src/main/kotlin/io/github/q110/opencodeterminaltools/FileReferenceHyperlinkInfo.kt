package io.github.q110.opencodeterminaltools

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

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

    private fun chooseBestFile(): VirtualFile? {
        if (files.size == 1) return files.first()

        if (requestedPath != null) {
            findByPathSuffix(requestedPath)?.let { return it }
        }

        val recentPath = recentFilePathsByName[fileName]
        if (recentPath != null) {
            findByPathSuffix(recentPath)?.let { return it }
        }

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

    private fun chooseFile(): VirtualFile? {
        val dialog = FileChoiceDialog(project, files)
        return if (dialog.showAndGet()) dialog.selectedFile else null
    }
}
