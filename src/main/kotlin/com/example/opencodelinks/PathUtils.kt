package com.example.opencodelinks

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

internal fun displayPath(project: Project, file: VirtualFile): String {
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val contentRoot = fileIndex.getContentRootForFile(file)
    if (contentRoot != null) {
        return VfsUtilCore.getRelativePath(file, contentRoot, '/') ?: file.path
    }

    val projectBasePath = project.basePath ?: return file.path
    return file.path.removePrefix(projectBasePath.replace('\\', '/') + "/")
}

internal fun pathMatches(project: Project, file: VirtualFile, path: String): Boolean {
    val normalizedPath = normalizePath(path)
    return normalizePath(displayPath(project, file)).endsWith(normalizedPath) ||
        normalizePath(file.path).endsWith(normalizedPath)
}

internal fun isPathReference(reference: String): Boolean {
    return reference.contains('/')
}

internal fun rangesOverlap(range: IntRange, ranges: List<IntRange>): Boolean {
    return ranges.any { range.first <= it.last && range.last >= it.first }
}

internal fun isCopyNoise(text: String): Boolean {
    return text.all { it == '_' || it == '-' || it == '.' || it == '/' || it.isDigit() } && text.none { it.isDigit() }
}

internal fun shortStatusText(text: String): String {
    return if (text.length > 80) text.take(77) + "..." else text
}

internal fun normalizePath(path: String): String {
    return path.replace('\\', '/').trim().trimEnd('.', ',', ';', ':', ')', ']', '}')
}
