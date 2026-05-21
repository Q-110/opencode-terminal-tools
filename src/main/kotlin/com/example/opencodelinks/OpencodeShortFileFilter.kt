package com.example.opencodelinks

import com.intellij.execution.filters.Filter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

internal class OpencodeShortFileFilter(
    private val project: Project
) : Filter {
    private val recentFilePathsByName = LinkedHashMap<String, String>()
    private val copyTextAttributes = TextAttributes()

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        rememberPathReferences(line)
        val baseOffset = entireLength - line.length
        val items = mutableListOf<Filter.ResultItem>()
        val fileLinkRanges = mutableListOf<IntRange>()

        for (match in FilterPatterns.fileRefPattern.findAll(line)) {
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
        for (match in FilterPatterns.fileRefPattern.findAll(line)) {
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
