// 配置持久化层 — APP 级单例，存储到 ai-terminal-tools.xml
package io.github.q110.aiterminaltools.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "AiTerminalToolsSettings",
    storages = [Storage("ai-terminal-tools.xml")]
)
class AiTerminalToolsSettings : PersistentStateComponent<AiTerminalToolsSettings.StateData> {
    private var state = StateData()

    override fun getState(): StateData {
        return state
    }

    override fun loadState(state: StateData) {
        this.state = state
    }

    /** 持久化字段定义，默认值均为开启 */
    class StateData {
        var fileLinksEnabled: Boolean = true
        var copyLinksEnabled: Boolean = true
        var errorToAiTerminalIconsEnabled: Boolean = true
        var dragToAiTerminalEnabled: Boolean? = null
        var commitMessageAiTool: String = "opencode"
        var commitMessageModel: String = ""
        var claudeCommitMessageModel: String = ""
        var commitMessageAdditionalPrompt: String = ""
        var additionalFileExtensions: String = ""

        /** 已有配置文件反序列化时缺少该字段，兜底默认开启 */
        fun isDragToAiTerminalEnabled(): Boolean {
            return dragToAiTerminalEnabled ?: true
        }

        /** 用户未配置附加提示词时使用插件默认附加提示词。 */
        fun resolvedCommitMessageAdditionalPrompt(): String {
            val customPrompt = commitMessageAdditionalPrompt.trim()
            return if (customPrompt.isNotEmpty()) customPrompt else DEFAULT_COMMIT_MESSAGE_ADDITIONAL_PROMPT
        }

        /** 合并默认扩展名和用户追加扩展名。 */
        fun resolvedFileExtensions(): Set<String> {
            val customExtensions = additionalFileExtensions
                .split(";")
                .map { it.trim().removePrefix(".").lowercase() }
                .filter { it.isNotEmpty() && it.matches(EXTENSION_PATTERN) }
                .toSet()

            return DEFAULT_FILE_EXTENSIONS + customExtensions
        }

        companion object {
            private val EXTENSION_PATTERN = Regex("[a-z][a-z0-9]*")

            const val DEFAULT_COMMIT_MESSAGE_BASE_PROMPT: String =
                "生成简洁的中文提交信息，按条目输出，不要过度思考，以最快的速度生成结果条目。"

            const val DEFAULT_COMMIT_MESSAGE_ADDITIONAL_PROMPT: String =
                "只写变更结果，不写技术细节。每条尽量短，避免出现反引号、Markdown 代码块、英文长句和具体实现描述，只输出普通文本条目。"

            val DEFAULT_FILE_EXTENSIONS = setOf(
                "java", "kt", "kts", "gradle",
                "js", "ts", "vue",
                "html", "css", "scss", "sass", "less",
                "py", "c", "cpp", "cc",
                "ps1", "cmd",
                "json", "toml", "yaml", "yml", "conf", "env", "properties", "xml",
                "md", "sql"
            )
        }
    }

    companion object {
        fun getInstance(): AiTerminalToolsSettings {
            return ApplicationManager.getApplication().getService(AiTerminalToolsSettings::class.java)
        }
    }
}
