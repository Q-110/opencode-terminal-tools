package com.example.consolelinks

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

// 获取适合展示给用户看的项目内路径，优先显示相对于内容根的路径。
internal fun displayPath(project: Project, file: VirtualFile): String {
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val contentRoot = fileIndex.getContentRootForFile(file)
    if (contentRoot != null) {
        return VfsUtilCore.getRelativePath(file, contentRoot, '/') ?: file.path
    }

    val projectBasePath = project.basePath ?: return file.path
    return file.path.removePrefix(projectBasePath.replace('\\', '/') + "/")
}

// 判断某个真实文件是否匹配控制台输出中的路径文本。
internal fun pathMatches(project: Project, file: VirtualFile, path: String): Boolean {
    val normalizedPath = normalizePath(path)
    // 同时比较项目内显示路径和真实磁盘路径，兼容相对路径与绝对路径。
    return normalizePath(displayPath(project, file)).endsWith(normalizedPath) ||
        normalizePath(file.path).endsWith(normalizedPath)
}

// 当前逻辑中，只要包含正斜杠就认为它是路径，而不是单纯文件名。
internal fun isPathReference(reference: String): Boolean {
    return reference.contains('/')
}

// 判断一个命中文本范围是否和已有范围重叠，避免同一段文字产生多个链接。
internal fun rangesOverlap(range: IntRange, ranges: List<IntRange>): Boolean {
    return ranges.any { range.first <= it.last && range.last >= it.first }
}

// 过滤没有实际复制价值的符号片段，例如纯分隔线。
internal fun isCopyNoise(text: String): Boolean {
    return text.all { it == '_' || it == '-' || it == '.' || it == '/' || it.isDigit() } && text.none { it.isDigit() }
}

// 把过长文本截短为适合状态提示展示的内容。
internal fun shortStatusText(text: String): String {
    return if (text.length > 80) text.take(77) + "..." else text
}

// 统一路径分隔符，并清理控制台输出中常见的尾部标点。
internal fun normalizePath(path: String): String {
    return path.replace('\\', '/').trim().trimEnd('.', ',', ';', ':', ')', ']', '}')
}
