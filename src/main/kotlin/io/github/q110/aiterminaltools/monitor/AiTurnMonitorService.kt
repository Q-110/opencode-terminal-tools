// Turn 状态机 — 按 tabId 维护每个终端的当前轮次
package io.github.q110.aiterminaltools.monitor

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class AiTurnMonitorService(
    private val project: Project
) {
    private val log = Logger.getInstance(AiTurnMonitorService::class.java)

    /** tabId → 已注册的终端上下文 */
    private val tabs = ConcurrentHashMap<String, AiTerminalTabContext>()

    /** tabId → 当前活跃的 turn */
    private val turns = ConcurrentHashMap<String, AiTurnState>()

    /** 注册新的 AI 终端 tab */
    fun registerTab(context: AiTerminalTabContext) {
        tabs[context.tabId] = context
        log.info("Registered AI terminal tab: ${context.tabId} (${context.tool})")
    }

    /** 注销 AI 终端 tab，同时清理关联的 turn */
    fun unregisterTab(tabId: String) {
        tabs.remove(tabId)
        val turn = turns.remove(tabId)
        if (turn != null && turn.changedFiles.isNotEmpty()) {
            log.info("Tab $tabId unregistered with ${turn.changedFiles.size} uncommitted changes, finishing turn")
            refreshAndShowDiff(turn)
        }
    }

    /** 获取所有活跃 turn（VFS 兜底监听用，第三阶段） */
    fun activeTurns(): List<AiTurnState> {
        return turns.values.toList()
    }

    /** 处理从 HTTP endpoint 接收到的事件 */
    fun handle(event: AiTurnEvent) {
        val tab = tabs[event.tabId]
        if (tab == null) {
            log.warn("Received event for unknown tabId: ${event.tabId}")
            return
        }

        if (!tab.accepts(event)) {
            log.warn("Token mismatch for tabId: ${event.tabId}")
            return
        }

        when (event.type) {
            AiTurnEventType.TURN_START -> startTurn(tab, event)
            AiTurnEventType.BEFORE_WRITE -> beforeWrite(tab, event)
            AiTurnEventType.FILE_CHANGED -> fileChanged(tab, event)
            AiTurnEventType.TURN_END -> finishTurn(tab, failed = false)
            AiTurnEventType.TURN_END_FAILED -> finishTurn(tab, failed = true)
        }
    }

    private fun startTurn(tab: AiTerminalTabContext, event: AiTurnEvent) {
        // 如已有未结束 turn，先完成上一轮
        val existing = turns[tab.tabId]
        if (existing != null) {
            log.info("Previous turn ${existing.turnId} for tab ${tab.tabId} not finished, auto-finishing")
            if (existing.changedFiles.isNotEmpty()) {
                refreshAndShowDiff(existing)
            }
        }

        val turnId = UUID.randomUUID().toString()
        turns[tab.tabId] = AiTurnState(
            turnId = turnId,
            tabId = tab.tabId,
            tool = tab.tool,
            startedAtMillis = System.currentTimeMillis(),
            cwd = tab.workingDirectory,
            upstreamSessionId = event.sessionId
        )
        log.info("Started turn $turnId for tab ${tab.tabId}")
    }

    private fun beforeWrite(tab: AiTerminalTabContext, event: AiTurnEvent) {
        val turn = turns[tab.tabId]
        if (turn == null) {
            log.debug("before_write received but no active turn for tab ${tab.tabId}, auto-starting turn")
            startTurn(tab, event)
            val newTurn = turns[tab.tabId] ?: return
            captureSnapshots(newTurn, event.paths)
            return
        }
        captureSnapshots(turn, event.paths)
    }

    private fun fileChanged(tab: AiTerminalTabContext, event: AiTurnEvent) {
        val turn = turns[tab.tabId]
        if (turn == null) {
            log.debug("file_changed received but no active turn for tab ${tab.tabId}")
            return
        }
        // 优先使用 hook 脚本提取的路径；如果为空（如 PostToolUse 不含 file_path），
        // 回退到已保存快照的路径集合。
        val paths = if (event.paths.isNotEmpty()) {
            event.paths.mapNotNull { normalizePath(turn.cwd, it) }
        } else {
            turn.beforeSnapshots.keys.toList()
        }
        for (path in paths) {
            turn.changedFiles.add(path)
        }
    }

    private fun finishTurn(tab: AiTerminalTabContext, failed: Boolean) {
        val turn = turns.remove(tab.tabId)
        if (turn == null) {
            log.debug("turn_end received but no active turn for tab ${tab.tabId}")
            return
        }

        log.info("Finishing turn ${turn.turnId} for tab ${tab.tabId}, " +
            "changed files: ${turn.changedFiles.size}, failed: $failed")

        if (turn.changedFiles.isEmpty()) {
            // 不弹 Diff，静默处理
            return
        }

        refreshAndShowDiff(turn)
    }

    private fun captureSnapshots(turn: AiTurnState, rawPaths: List<String>) {
        val snapshotService = project.service<AiTurnSnapshotService>()
        val paths = rawPaths.mapNotNull { normalizePath(turn.cwd, it) }
        for (path in paths) {
            snapshotService.captureBeforeIfAbsent(turn, path)
        }
    }

    private fun refreshAndShowDiff(turn: AiTurnState) {
        // 先刷新文件系统，确保 IDEA 看到最新内容
        try {
            val files = turn.changedFiles.map { it.toFile() }
            LocalFileSystem.getInstance().refreshIoFiles(files, false, true, null)
        } catch (exception: Throwable) {
            log.warn("Failed to refresh files", exception)
        }

        project.service<AiTurnDiffPresenter>().showDiff(turn)
    }

    /**
     * 将原始路径标准化为绝对路径，并进行安全校验。
     * - 支持相对路径和绝对路径
     * - 支持 `\` 和 `/` 混用
     * - 禁止越出 project base path
     * - 忽略黑名单目录中的文件
     */
    internal fun normalizePath(cwd: Path, raw: String): Path? {
        val cleaned = raw.trim()
            .removePrefix("@")

        if (cleaned.isBlank()) return null

        val cleanedPath = cleaned.replace('\\', '/')

        val path = try {
            Path.of(cleanedPath)
        } catch (_: Throwable) {
            return null
        }

        val absolute = if (path.isAbsolute) path else cwd.resolve(path)
        val normalized = absolute.normalize()

        // 安全检查：禁止越出 project base path
        val projectBase = project.basePath?.let { Path.of(it).normalize() } ?: return null
        if (!normalized.startsWith(projectBase)) {
            log.debug("Path $normalized is outside project base $projectBase, ignoring")
            return null
        }

        // 检查忽略目录
        val relativePath = projectBase.relativize(normalized).toString()
        for (ignored in IGNORED_DIRECTORIES) {
            if (relativePath.startsWith("$ignored/") || relativePath.startsWith("$ignored${File.separator}")) {
                log.debug("Path $normalized is in ignored directory $ignored, skipping")
                return null
            }
        }

        return normalized
    }

    companion object {
        private val IGNORED_DIRECTORIES = setOf(
            ".git", "node_modules", "target", "build", "dist", ".idea"
        )
    }
}
