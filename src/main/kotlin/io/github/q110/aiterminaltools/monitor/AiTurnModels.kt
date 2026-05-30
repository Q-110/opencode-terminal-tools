// monitor 模块共用数据模型
package io.github.q110.aiterminaltools.monitor

import java.nio.charset.Charset
import java.nio.file.Path

/** AI 终端工具类型 */
enum class AiTool {
    OPENCODE,
    CLAUDE_CODE
}

/** Turn 事件类型 */
enum class AiTurnEventType {
    TURN_START,
    BEFORE_WRITE,
    FILE_CHANGED,
    TURN_END,
    TURN_END_FAILED
}

/** 从 HTTP endpoint 接收到的事件 */
data class AiTurnEvent(
    val source: AiTool,
    val type: AiTurnEventType,
    val tabId: String,
    val token: String?,
    val sessionId: String?,
    val paths: List<String>,
    val rawJson: String
)

/** 一轮对话的状态 */
data class AiTurnState(
    val turnId: String,
    val tabId: String,
    val tool: AiTool,
    val startedAtMillis: Long,
    val cwd: Path,
    val upstreamSessionId: String?,
    val beforeSnapshots: MutableMap<Path, FileSnapshot> = linkedMapOf(),
    val changedFiles: LinkedHashSet<Path> = linkedSetOf()
)

/** 文件修改前快照 */
sealed interface FileSnapshot {
    /** 文件不存在（新增场景） */
    data object Missing : FileSnapshot

    /** 文本文件快照 */
    data class Text(
        val text: String,
        val charset: Charset,
        val fileTypeName: String?
    ) : FileSnapshot

    /** 二进制文件快照 */
    data class Binary(
        val bytes: ByteArray,
        val fileTypeName: String?
    ) : FileSnapshot {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Binary) return false
            return bytes.contentEquals(other.bytes) && fileTypeName == other.fileTypeName
        }

        override fun hashCode(): Int {
            return 31 * bytes.contentHashCode() + (fileTypeName?.hashCode() ?: 0)
        }
    }
}

/** 已注册的 AI 终端 tab 上下文 */
data class AiTerminalTabContext(
    val tabId: String,
    val token: String,
    val tool: AiTool,
    val workingDirectory: Path,
    val createdAtMillis: Long
) {
    /** 校验事件 token：事件未携带 token 时放行，否则必须匹配 */
    fun accepts(event: AiTurnEvent): Boolean {
        return event.token == null || event.token == token
    }
}
