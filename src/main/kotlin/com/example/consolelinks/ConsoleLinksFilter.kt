package com.example.consolelinks

import com.intellij.execution.filters.Filter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

// 控制台输出过滤器：把文件引用转换为跳转链接，把可复制文本转换为复制链接。
internal class ConsoleLinksFilter(
    private val project: Project
) : Filter {
    // 记录最近出现过的完整路径，用于后续只有短文件名时辅助选择正确文件。
    private val recentFilePathsByName = LinkedHashMap<String, String>()

    // 复制链接不额外改变颜色样式，尽量保持控制台原始显示效果。
    private val copyTextAttributes = TextAttributes()

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val settings = ConsoleLinksSettings.getInstance().getState()
        if (!settings.fileLinksEnabled && !settings.copyLinksEnabled) {
            return null
        }

        if (settings.fileLinksEnabled) {
            // 先缓存本行里的路径引用，后续同名文件消歧时可以使用。
            rememberPathReferences(line)
        }

        // entireLength 是控制台截至当前行末尾的总长度，减去当前行长度即可得到本行起始偏移。
        val baseOffset = entireLength - line.length
        val items = mutableListOf<Filter.ResultItem>()
        val fileLinkRanges = mutableListOf<IntRange>()

        if (settings.fileLinksEnabled) {
            // 第一轮优先识别文件引用，因为文件跳转的优先级高于点击复制。
            for (match in FilterPatterns.fileRefPattern.findAll(line)) {
                val reference = normalizePath(match.groupValues[1])
                val fileName = reference.substringAfterLast('/')
                val requestedPath = if (isPathReference(reference)) reference else null
                val hasLineNumber = match.groupValues[2].isNotEmpty()
                val lineNumber = match.groupValues[2].toIntOrNull() ?: 1
                val endLineNumber = match.groupValues[3].toIntOrNull()
                val files = findProjectFiles(fileName, requestedPath)
                if (files.isEmpty()) continue

                // 记录文件链接范围，避免同一段文本又被复制规则重复命中。
                fileLinkRanges += match.range
                items += Filter.ResultItem(
                    baseOffset + match.range.first,
                    baseOffset + match.range.last + 1,
                    FileReferenceHyperlinkInfo(
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
        }

        if (settings.copyLinksEnabled) {
            // 第二轮识别可复制文本，并跳过已经被文件链接占用的范围。
            for (match in findCopyMatches(line, fileLinkRanges)) {
                items += Filter.ResultItem(
                    baseOffset + match.range.first,
                    baseOffset + match.range.last + 1,
                    CopyTextHyperlinkInfo(project, match.text),
                    copyTextAttributes,
                    copyTextAttributes,
                    copyTextAttributes
                )
            }
        }

        return if (items.isEmpty()) null else Filter.Result(items)
    }

    private fun findProjectFiles(fileName: String, requestedPath: String?): List<VirtualFile> {
        // IntelliJ PSI/VFS 查询需要在读操作中执行，避免和 IDE 内部写操作冲突。
        return ReadAction.compute<List<VirtualFile>, RuntimeException> {
            val files = FilenameIndex
                .getVirtualFilesByName(fileName, GlobalSearchScope.projectScope(project))
                .filter { it.isValid && !it.isDirectory }
                .sortedBy { displayPath(project, it) }

            // 如果用户输出的是完整/相对路径，就进一步按路径过滤；短文件名则保留所有同名文件。
            if (requestedPath == null) files else files.filter { pathMatches(project, it, requestedPath) }
        }
    }

    private fun rememberPathReferences(line: String) {
        // 只缓存带目录的路径引用，单独文件名无法帮助区分同名文件。
        for (match in FilterPatterns.fileRefPattern.findAll(line)) {
            val path = normalizePath(match.groupValues[1])
            if (!isPathReference(path)) continue

            val fileName = path.substringAfterLast('/')
            recentFilePathsByName[fileName] = path
        }

        // 限制缓存数量，避免长时间运行控制台时无限增长。
        while (recentFilePathsByName.size > 200) {
            val firstKey = recentFilePathsByName.keys.firstOrNull() ?: break
            recentFilePathsByName.remove(firstKey)
        }
    }

    private fun findCopyMatches(line: String, blockedRanges: List<IntRange>): List<CopyMatch> {
        // usedRanges 会随着命中不断追加，保证复制链接之间也不会相互重叠。
        val usedRanges = blockedRanges.toMutableList()
        val matches = mutableListOf<CopyMatch>()

        for (pattern in FilterPatterns.copyPatterns) {
            for (match in pattern.findAll(line)) {
                if (rangesOverlap(match.range, usedRanges)) continue

                val text = match.value.trim()
                if (text.isEmpty() || isCopyNoise(text)) continue

                usedRanges += match.range
                matches += CopyMatch(match.range, text)
            }
        }

        // 按文本出现顺序返回，方便 IDEA 按控制台顺序渲染链接。
        return matches.sortedBy { it.range.first }
    }
}

// 保存一次复制链接的命中文本范围和实际复制内容。
private data class CopyMatch(
    val range: IntRange,
    val text: String
)
