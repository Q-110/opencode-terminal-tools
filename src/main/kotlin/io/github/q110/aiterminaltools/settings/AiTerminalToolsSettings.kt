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
        var commitMessageModel: String = ""
        var additionalFileExtensions: String = ""

        /** 已有配置文件反序列化时缺少该字段，兜底默认开启 */
        fun isDragToAiTerminalEnabled(): Boolean {
            return dragToAiTerminalEnabled ?: true
        }

        /** 合并默认扩展名和用户追加扩展名。 */
        fun resolvedFileExtensions(): Set<String> {
            val customExtensions = additionalFileExtensions
                .split(",", ";", " ", "\n")
                .map { it.trim().removePrefix(".").lowercase() }
                .filter { it.isNotEmpty() && it.matches(EXTENSION_PATTERN) }
                .toSet()

            return DEFAULT_FILE_EXTENSIONS + customExtensions
        }

        companion object {
            private val EXTENSION_PATTERN = Regex("[a-z][a-z0-9]*")

            val DEFAULT_FILE_EXTENSIONS = setOf(
                "java", "kt", "kts", "scala", "groovy", "gradle",
                "js", "jsx", "mjs", "cjs", "ts", "tsx", "vue", "svelte",
                "html", "htm", "css", "scss", "sass", "less",
                "py", "pyi", "pyx", "go", "rs", "rb", "php", "swift",
                "c", "cpp", "cc", "cxx", "h", "hpp", "hh", "hxx",
                "sh", "bash", "zsh", "ps1", "bat", "cmd",
                "json", "jsonc", "toml", "yaml", "yml", "ini", "cfg", "conf",
                "env", "properties", "proto", "xml", "xsl", "xsd",
                "md", "mdx", "rst", "tex", "adoc",
                "sql",
                "j2", "jinja", "jinja2", "mustache", "hbs", "ejs", "twig"
            )
        }
    }

    companion object {
        fun getInstance(): AiTerminalToolsSettings {
            return ApplicationManager.getApplication().getService(AiTerminalToolsSettings::class.java)
        }
    }
}
