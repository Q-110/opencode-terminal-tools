// 路径工具函数 — VirtualFile 查找、规范化、匹配
package io.github.q110.aiterminaltools.filter

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/** 将 VirtualFile 转为相对于项目的显示路径（优先项目基路径 → 内容根 → 绝对路径） */
internal fun displayPath(project: Project, file: VirtualFile): String {
    val projectBasePath = project.basePath
    if (projectBasePath != null) {
        val normalizedBasePath = projectBasePath.replace('\\', '/')
        if (file.path == normalizedBasePath) {
            return "."
        }
        if (file.path.startsWith("$normalizedBasePath/")) {
            return file.path.removePrefix("$normalizedBasePath/")
        }
    }

    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val contentRoot = fileIndex.getContentRootForFile(file)
    if (contentRoot != null) {
        return VfsUtilCore.getRelativePath(file, contentRoot, '/') ?: file.path
    }

    return file.path
}

/** 判断文件路径是否以给定路径后缀结尾（均经标准化后比较） */
internal fun pathMatches(project: Project, file: VirtualFile, path: String): Boolean {
    val normalizedPath = normalizePath(path)
    return normalizePath(displayPath(project, file)).endsWith(normalizedPath) ||
        normalizePath(file.path).endsWith(normalizedPath)
}

/** 在项目中查找字符串路径对应的 VirtualFile（查项目根 → 查内容根） */
internal fun findProjectPath(project: Project, path: String): VirtualFile? {
    val normalizedPath = normalizePath(path).trimStart('/')
    val roots = mutableListOf<VirtualFile>()
    val basePath = project.basePath
    if (basePath != null) {
        LocalFileSystem.getInstance().findFileByPath(basePath.replace('\\', '/'))?.let { roots += it }
    }
    roots += ProjectRootManager.getInstance(project).contentRoots

    return roots.asSequence()
        .distinctBy { it.path }
        .mapNotNull { it.findFileByRelativePath(normalizedPath) }
        .firstOrNull()
}

/** 判断引用字符串是否包含路径分隔符 */
internal fun isPathReference(reference: String): Boolean {
    return reference.contains('/')
}

/** 判断两个区间是否重叠（用于 fileLinks 与 copyLinks 去重） */
internal fun rangesOverlap(range: IntRange, ranges: List<IntRange>): Boolean {
    return ranges.any { range.first <= it.last && range.last >= it.first }
}

/** 过滤无意义的纯符号文本（全为 _ - . / 且不含数字） */
internal fun isCopyNoise(text: String): Boolean {
    return text.all { it == '_' || it == '-' || it == '.' || it == '/' || it.isDigit() } && text.none { it.isDigit() }
}

/** 截断长文本用于通知展示 */
internal fun shortStatusText(text: String): String {
    return if (text.length > 80) text.take(77) + "..." else text
}

/** 标准化路径：统一正斜杠、去除空格和尾部标点 */
internal fun normalizePath(path: String): String {
    return path.replace('\\', '/').trim().trimEnd('.', ',', ';', ':', ')', ']', '}')
}
