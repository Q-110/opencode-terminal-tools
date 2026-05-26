// 核心 Filter — 解析终端每一行输出，生成文件跳转链接和点击复制链接
package io.github.q110.aiterminaltools.filter

import com.intellij.execution.filters.Filter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import io.github.q110.aiterminaltools.copy.CopyTextHyperlinkInfo
import io.github.q110.aiterminaltools.jump.FileReferenceHyperlinkInfo
import io.github.q110.aiterminaltools.jump.FolderReferenceHyperlinkInfo
import io.github.q110.aiterminaltools.settings.AiTerminalToolsSettings

internal class AiTerminalToolsFilter(
    private val project: Project
) : Filter {
    /** LRU 缓存：文件名 → 最近出现的路径 */
    private val recentFilePathsByName = LinkedHashMap<String, String>()
    private val copyTextAttributes = TextAttributes()
    private var cachedFileExtensions: Set<String> = emptySet()
    private var cachedFileRefPattern: Regex =
        FilterPatterns.fileRefPattern(AiTerminalToolsSettings.StateData.DEFAULT_FILE_EXTENSIONS)

    /** @param line 当前输出行文本，entireLength 整段内容总长度（用于计算 baseOffset） */
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val settings = AiTerminalToolsSettings.getInstance().getState()
        if (!settings.fileLinksEnabled && !settings.copyLinksEnabled) {
            return null
        }

        // 阶段一：缓存当前行中的文件路径引用
        if (settings.fileLinksEnabled) {
            val fileRefPattern = currentFileRefPattern(settings.resolvedFileExtensions())
            rememberPathReferences(line, fileRefPattern)
        }

        val baseOffset = entireLength - line.length
        val items = mutableListOf<Filter.ResultItem>()
        val fileLinkRanges = mutableListOf<IntRange>()

        // 阶段二：解析 @路径引用（高优先级，精确匹配）
        if (settings.fileLinksEnabled) {
            for (match in FilterPatterns.atPathRefPattern.findAll(line)) {
                val reference = normalizePath(match.groupValues[1])
                val target = findProjectPathReference(reference) ?: continue
                val hasLineNumber = match.groupValues[2].isNotEmpty()
                val lineNumber = match.groupValues[2].toIntOrNull() ?: 1
                val endLineNumber = match.groupValues[3].toIntOrNull()

                fileLinkRanges += match.range
                items += if (target.isDirectory) {
                    Filter.ResultItem(
                        baseOffset + match.range.first,
                        baseOffset + match.range.last + 1,
                        FolderReferenceHyperlinkInfo(project, target)
                    )
                } else {
                    Filter.ResultItem(
                        baseOffset + match.range.first,
                        baseOffset + match.range.last + 1,
                        FileReferenceHyperlinkInfo(
                            project,
                            listOf(target),
                            target.name,
                            reference,
                            hasLineNumber,
                            lineNumber,
                            endLineNumber,
                            recentFilePathsByName.toMap()
                        )
                    )
                }
            }

            // 阶段三：解析常规文件引用（按文件名索引，可能多匹配）
            val fileRefPattern = currentFileRefPattern(settings.resolvedFileExtensions())
            for (match in fileRefPattern.findAll(line)) {
                if (rangesOverlap(match.range, fileLinkRanges)) continue

                val reference = normalizePath(match.groupValues[1])
                val fileName = reference.substringAfterLast('/')
                val requestedPath = if (isPathReference(reference)) reference else null
                val hasLineNumber = match.groupValues[2].isNotEmpty()
                val lineNumber = match.groupValues[2].toIntOrNull() ?: 1
                val endLineNumber = match.groupValues[3].toIntOrNull()
                val files = findProjectFiles(fileName, requestedPath)
                if (files.isEmpty()) continue

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

        // 阶段四：解析点击复制模式（跳过已被文件链接占用的区间）
        if (settings.copyLinksEnabled) {
            for (match in findCopyMatches(line, fileLinkRanges)) {
                items += Filter.ResultItem(
                    baseOffset + match.range.first,
                    baseOffset + match.range.last + 1,
                    CopyTextHyperlinkInfo(project, match.text),
                    copyTextAttributes
                )
            }
        }

        return if (items.isEmpty()) null else Filter.Result(items)
    }

    /** 复用已编译正则，仅在设置中的扩展名集合变化时刷新。 */
    private fun currentFileRefPattern(extensions: Set<String>): Regex {
        if (extensions != cachedFileExtensions) {
            cachedFileExtensions = extensions
            cachedFileRefPattern = FilterPatterns.fileRefPattern(extensions)
        }

        return cachedFileRefPattern
    }

    /** 在 ReadAction 中按路径查找 VirtualFile */
    private fun findProjectPathReference(path: String): VirtualFile? {
        return ReadAction.compute<VirtualFile?, RuntimeException> {
            findProjectPath(project, path)
        }
    }

    /** 在 ReadAction 中按文件名查找项目文件，可选的路径过滤 */
    private fun findProjectFiles(fileName: String, requestedPath: String?): List<VirtualFile> {
        return ReadAction.compute<List<VirtualFile>, RuntimeException> {
            val files = FilenameIndex
                .getVirtualFilesByName(fileName, GlobalSearchScope.projectScope(project))
                .filter { it.isValid && !it.isDirectory }
                .sortedBy { displayPath(project, it) }

            if (requestedPath == null) files else files.filter { pathMatches(project, it, requestedPath) }
        }
    }

    /** 缓存当前行中的路径到 LRU map，供后续同名文件消除歧义 */
    private fun rememberPathReferences(line: String, fileRefPattern: Regex) {
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

    /** 扫描 copyPatterns，按优先级匹配并跳过已占用的区间 */
    private fun findCopyMatches(line: String, blockedRanges: List<IntRange>): List<CopyMatch> {
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

        return matches.sortedBy { it.range.first }
    }
}

private data class CopyMatch(
    val range: IntRange,
    val text: String
)
