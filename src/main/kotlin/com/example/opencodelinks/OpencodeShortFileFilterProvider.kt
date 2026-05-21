package com.example.opencodelinks

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class OpencodeShortFileFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> {
        return arrayOf(OpencodeShortFileFilter(project))
    }
}

private class OpencodeShortFileFilter(
    private val project: Project
) : Filter {
    private val recentFilePathsByName = LinkedHashMap<String, String>()
    private val supportedExtensions = "java|kt|kts|js|jsx|ts|tsx|vue|xml|html|css|scss|yml|yaml|properties|sql|md"
    private val fileNamePattern = """[A-Za-z_$][A-Za-z0-9_.$-]*\.(?:$supportedExtensions)"""
    private val pathPattern = """(?:(?:[A-Za-z]:)?[\\/]|\.{1,2}[\\/])?[A-Za-z0-9_.$-]+(?:[\\/][A-Za-z0-9_.$-]+)+\.(?:$supportedExtensions)"""
    private val fileRefPattern = Regex(
        """(?<![\\/A-Za-z0-9_.$-])($pathPattern|$fileNamePattern)(?::(\d+)(?:-(\d+))?)?(?![\d\w.$-])"""
    )

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        rememberPathReferences(line)
        val baseOffset = entireLength - line.length
        val items = mutableListOf<Filter.ResultItem>()

        for (match in fileRefPattern.findAll(line)) {
            val reference = normalizePath(match.groupValues[1])
            val fileName = reference.substringAfterLast('/')
            val requestedPath = if (isPathReference(reference)) reference else null
            val hasLineNumber = match.groupValues[2].isNotEmpty()
            val lineNumber = match.groupValues[2].toIntOrNull() ?: 1
            val endLineNumber = match.groupValues[3].toIntOrNull()
            val files = findProjectFiles(fileName, requestedPath)
            if (files.isEmpty()) continue

            items += Filter.ResultItem(
                baseOffset + match.range.first,
                baseOffset + match.range.last + 1,
                ShortFileHyperlinkInfo(
                    project,
                    files,
                    fileName,
                    requestedPath,
                    hasLineNumber,
                    lineNumber,
                    endLineNumber,
                    recentFilePathsByName.toMap()
                )
            )
        }

        return if (items.isEmpty()) null else Filter.Result(items)
    }

    private fun findProjectFiles(fileName: String, requestedPath: String?): List<VirtualFile> {
        return ReadAction.compute<List<VirtualFile>, RuntimeException> {
            val files = FilenameIndex
                .getVirtualFilesByName(fileName, GlobalSearchScope.projectScope(project))
                .filter { it.isValid && !it.isDirectory }
                .sortedBy { displayPath(project, it) }

            if (requestedPath == null) files else files.filter { pathMatches(project, it, requestedPath) }
        }
    }

    private fun rememberPathReferences(line: String) {
        for (match in fileRefPattern.findAll(line)) {
            val path = normalizePath(match.groupValues[1])
            if (!isPathReference(path)) continue

            val fileName = path.substringAfterLast('/')
            recentFilePathsByName[fileName] = path
        }

        while (recentFilePathsByName.size > 200) {
            val firstKey = recentFilePathsByName.keys.firstOrNull() ?: break
            recentFilePathsByName.remove(firstKey)
        }
    }
}

private class ShortFileHyperlinkInfo(
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

private class FileChoiceDialog(
    private val project: Project,
    private val files: List<VirtualFile>
) : DialogWrapper(project) {
    private val list = JBList(files.map { displayPath(project, it) })

    val selectedFile: VirtualFile?
        get() = files.getOrNull(list.selectedIndex)

    init {
        title = "选择文件"
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.selectedIndex = 0
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(JLabel("发现多个同名文件，请选择要跳转的文件。"), BorderLayout.NORTH)
        panel.add(JBScrollPane(list), BorderLayout.CENTER)
        panel.preferredSize = dialogSize(project)
        return panel
    }

    private fun dialogSize(project: Project): Dimension {
        val windowSize = WindowManager.getInstance().getFrame(project)?.size
            ?: Toolkit.getDefaultToolkit().screenSize
        return Dimension((windowSize.width * 0.5).toInt(), (windowSize.height * 0.5).toInt())
    }
}

private fun displayPath(project: Project, file: VirtualFile): String {
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val contentRoot = fileIndex.getContentRootForFile(file)
    if (contentRoot != null) {
        return VfsUtilCore.getRelativePath(file, contentRoot, '/') ?: file.path
    }

    val projectBasePath = project.basePath ?: return file.path
    return file.path.removePrefix(projectBasePath.replace('\\', '/') + "/")
}

private fun pathMatches(project: Project, file: VirtualFile, path: String): Boolean {
    val normalizedPath = normalizePath(path)
    return normalizePath(displayPath(project, file)).endsWith(normalizedPath) ||
        normalizePath(file.path).endsWith(normalizedPath)
}

private fun isPathReference(reference: String): Boolean {
    return reference.contains('/')
}

private fun normalizePath(path: String): String {
    return path.replace('\\', '/').trim().trimEnd('.', ',', ';', ':', ')', ']', '}')
}
