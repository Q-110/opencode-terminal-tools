// OpenCode 桥接核心服务 — 选区写入桥接文件 → 触发 OpenCode editor_open 快捷键
package io.github.q110.opencodeterminaltools.bridge

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.ui.TerminalWidget
import io.github.q110.opencodeterminaltools.settings.OpenCodeTerminalToolsSettings
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.swing.Timer

@Service(Service.Level.PROJECT)
class OpenCodeBridgeService(
    private val project: Project
) {
    /** 用户手动标记的目标终端（优先使用） */
    private var markedTerminal: TargetTerminal? = null

    /** 新版终端辅助类，仅在 2025.3+ IDE 中可加载，低版本为 null */
    private val frontendHelper: FrontendTerminalHelper? = try {
        FrontendTerminalHelper(project)
    } catch (_: Throwable) {
        null
    }

    /** 标记当前终端：右键菜单 → 当前终端 → 选中标签页 → 唯一可用终端 */
    fun markTerminal(dataContext: DataContext): Boolean {
        val terminal = classicTerminalFromDataContext(dataContext)?.let { TargetTerminal.Classic(it) }
            ?: selectedClassicTerminal()?.let { TargetTerminal.Classic(it) }
            ?: selectedTerminal()
            ?: singleUsableTerminal()
            ?: return false
        markedTerminal = terminal
        return true
    }

    /** 检查是否有可用终端 */
    fun canFindTerminal(dataContext: DataContext): Boolean {
        return resolveTargetTerminal(dataContext) != null
    }

    /** 两阶段发送：写入桥接文件 → 注入 editor_open 到终端 */
    fun sendSelection(payload: String, dataContext: DataContext, settleAtLineEnd: Boolean = false): BridgeResult {
        return try {
            writeBridgeFiles(payload)
            injectEditorCommand(dataContext, settleAtLineEnd)
        } catch (exception: IOException) {
            BridgeResult.Error("写入 OpenCode 桥接文件失败：${exception.message}")
        }
    }

    /** 阶段一：在 %TEMP%\opencode-idea-bridge\ 下写入选区文件及桥接脚本 */
    private fun writeBridgeFiles(payload: String) {
        Files.createDirectories(bridgeDir)
        Files.writeString(selectionFile, payload, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        Files.writeString(powerShellScript, powerShellScriptContent(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        Files.writeString(cmdScript, cmdScriptContent(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
    }

    /** 阶段二：找到目标终端并触发 editor_open */
    private fun injectEditorCommand(dataContext: DataContext, settleAtLineEnd: Boolean): BridgeResult {
        val terminal = resolveTargetTerminal(dataContext)
            ?: return BridgeResult.Error("没有找到可写入的 OpenCode Terminal。请先在 OpenCode 所在 Terminal 标签页执行 Mark as OpenCode Terminal。")

        return when (terminal) {
            is TargetTerminal.Classic -> injectClassicTerminal(terminal.widget, settleAtLineEnd)
            is TargetTerminal.Frontend -> {
                val helper = frontendHelper
                    ?: return BridgeResult.Error("新版终端 API 在当前 IDE 中不可用。")
                val trigger = editorOpenTrigger()
                helper.inject(terminal.tab, settleAtLineEnd, trigger.text, trigger.isCommand)
            }
        }
    }

    /** 通过 TTY Connector 向经典终端写入快捷键序列 */
    private fun injectClassicTerminal(terminal: TerminalWidget, settleAtLineEnd: Boolean): BridgeResult {
        val connector = try {
            terminal.ttyConnector
        } catch (exception: Throwable) {
            return BridgeResult.Error("当前 Terminal 没有暴露可写入的 TTY 连接。")
        } ?: return BridgeResult.Error("当前 Terminal 没有暴露可写入的 TTY 连接。")

        if (!connector.isConnected) {
            markedTerminal = null
            return BridgeResult.Error("已选择的 OpenCode Terminal 已断开连接。")
        }

        val trigger = try {
            editorOpenTrigger()
        } catch (exception: IllegalArgumentException) {
            return BridgeResult.Error(exception.message ?: "OpenCode editor_open 快捷键配置无效。")
        }

        return try {
            terminal.requestFocus()
            connector.write(if (trigger.isCommand) trigger.text + "\r" else trigger.text)
            if (settleAtLineEnd) {
                scheduleClassicLineEndSpace { connector.write(LINE_END_SPACE) }
            }
            BridgeResult.Success
        } catch (exception: IOException) {
            BridgeResult.Error("发送 OpenCode editor_open 到 Terminal 失败：${exception.message}")
        }
    }

    /** 经典终端上行尾空格延时 300ms（等待 OpenCode 处理完输入） */
    private fun scheduleClassicLineEndSpace(writeLineEndSpace: () -> Unit) {
        Timer(SETTLE_INPUT_DELAY_MS) {
            try {
                writeLineEndSpace()
            } catch (exception: Throwable) {
                notify(project, "发送 OpenCode 行尾空格失败：${exception.message}", NotificationType.WARNING)
            }
        }.apply {
            isRepeats = false
            start()
        }
    }

    /** 从用户配置中解析 editor_open 触发命令 */
    private fun editorOpenTrigger(): EditorOpenTrigger {
        val configured = OpenCodeTerminalToolsSettings.getInstance()
            .getState()
            .openCodeEditorOpenShortcut
            .trim()
            .ifEmpty { DEFAULT_EDITOR_OPEN_SHORTCUT }
        if (configured.startsWith("/")) {
            return EditorOpenTrigger(configured, true)
        }
        return EditorOpenTrigger(shortcutToTerminalText(configured), false)
    }

    /** 将快捷键描述（如 "ctrl+x e"）转为终端控制字符序列 */
    private fun shortcutToTerminalText(shortcut: String): String {
        return shortcut.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(separator = "") { token -> shortcutTokenToTerminalText(token) }
    }

    /** 单个快捷键 token 转义：ctrl+x→\u0018, enter→\r, F1-F12→转义序列 */
    private fun shortcutTokenToTerminalText(token: String): String {
        val normalized = token.lowercase()
        if (normalized.startsWith("ctrl+")) {
            val key = normalized.removePrefix("ctrl+")
            if (key.length == 1 && key[0] in 'a'..'z') {
                return ((key[0].code - 'a'.code + 1).toChar()).toString()
            }
        }

        return when (normalized) {
            "enter" -> "\r"
            "esc", "escape" -> "\u001B"
            "tab" -> "\t"
            "space" -> " "
            "f1" -> "\u001BOP"
            "f2" -> "\u001BOQ"
            "f3" -> "\u001BOR"
            "f4" -> "\u001BOS"
            "f5" -> "\u001B[15~"
            "f6" -> "\u001B[17~"
            "f7" -> "\u001B[18~"
            "f8" -> "\u001B[19~"
            "f9" -> "\u001B[20~"
            "f10" -> "\u001B[21~"
            "f11" -> "\u001B[23~"
            "f12" -> "\u001B[24~"
            else -> if (token.length == 1) token else throw IllegalArgumentException("不支持的 OpenCode editor_open 快捷键片段：$token")
        }
    }

    /** 终端发现优先级：已标记 → DataContext → 选中标签页 → 唯一可用 */
    private fun resolveTargetTerminal(dataContext: DataContext): TargetTerminal? {
        val marked = markedTerminal
        if (marked != null) {
            if (isUsable(marked)) return marked
            markedTerminal = null
        }
        classicTerminalFromDataContext(dataContext)?.let {
            val target = TargetTerminal.Classic(it)
            if (isUsable(target)) return target
        }
        selectedTerminal()?.let { if (isUsable(it)) return it }
        return singleUsableTerminal()
    }

    private fun classicTerminalFromDataContext(dataContext: DataContext): TerminalWidget? {
        return JBTerminalWidget.TERMINAL_DATA_KEY.getData(dataContext)?.asNewWidget()
    }

    /** 优先前端终端 → 经典终端 */
    private fun selectedTerminal(): TargetTerminal? {
        return frontendHelper?.selectedTerminal()?.let { TargetTerminal.Frontend(it) }
            ?: selectedClassicTerminal()?.let { TargetTerminal.Classic(it) }
    }

    private fun selectedClassicTerminal(): TerminalWidget? {
        val toolWindow = TerminalToolWindowManager.getInstance(project).toolWindow ?: return null
        val selectedContent = toolWindow.contentManager.selectedContent ?: return null
        return TerminalToolWindowManager.findWidgetByContent(selectedContent)
    }

    /** 唯一可用终端（前端 + 经典合并列表） */
    private fun singleUsableTerminal(): TargetTerminal? {
        val frontendTerminals = frontendHelper?.allTerminals()
            ?.map { TargetTerminal.Frontend(it) }
            ?.filter { isUsable(it) }
            .orEmpty()
        val classicTerminals = TerminalToolWindowManager.getInstance(project)
            .terminalWidgets
            .map { TargetTerminal.Classic(it) }
            .filter { isUsable(it) }
        return (frontendTerminals + classicTerminals).singleOrNull()
    }

    /** 检查终端是否可用：经典终端检查 TTY 连接，前端终端检查 tab 仍存在 */
    private fun isUsable(terminal: TargetTerminal): Boolean {
        return when (terminal) {
            is TargetTerminal.Classic -> {
                try {
                    terminal.widget.ttyConnector?.isConnected == true
                } catch (_: Throwable) {
                    false
                }
            }
            is TargetTerminal.Frontend -> frontendHelper?.isTabExists(terminal.tab) == true
        }
    }

    /** PowerShell 桥接脚本：读取选区内容，拼接到 OpenCode 传入的临时编辑文件 */
    private fun powerShellScriptContent(): String {
        return """
            param([string]${'$'}TargetFile)
            if ([string]::IsNullOrWhiteSpace(${'$'}TargetFile)) { exit 1 }

            ${'$'}BridgeDir = Split-Path -Parent ${'$'}MyInvocation.MyCommand.Path
            ${'$'}SelectionFile = Join-Path ${'$'}BridgeDir 'latest-selection.md'
            if (-not (Test-Path -LiteralPath ${'$'}SelectionFile)) { exit 0 }

            ${'$'}Utf8NoBom = New-Object System.Text.UTF8Encoding(${'$'}false)
            ${'$'}Original = ""
            if (Test-Path -LiteralPath ${'$'}TargetFile) {
              ${'$'}Original = [System.IO.File]::ReadAllText(${'$'}TargetFile, [System.Text.Encoding]::UTF8)
            }
            ${'$'}Selection = [System.IO.File]::ReadAllText(${'$'}SelectionFile, [System.Text.Encoding]::UTF8)

            if ([string]::IsNullOrEmpty(${'$'}Original)) {
              ${'$'}Merged = ${'$'}Selection
            } else {
              ${'$'}Merged = ${'$'}Original.TrimEnd("`r", "`n") + [Environment]::NewLine + ${'$'}Selection
            }

            [System.IO.File]::WriteAllText(${'$'}TargetFile, ${'$'}Merged, ${'$'}Utf8NoBom)

            ${'$'}RealEditor = ${'$'}env:OPENCODE_IDEA_REAL_EDITOR
            if (-not [string]::IsNullOrWhiteSpace(${'$'}RealEditor)) {
              ${'$'}QuotedTargetFile = '"' + ${'$'}TargetFile.Replace('"', '\"') + '"'
              cmd /c "${'$'}RealEditor ${'$'}QuotedTargetFile"
              exit ${'$'}LASTEXITCODE
            }

            exit 0
        """.trimIndent()
    }

    /** CMD 桥接脚本：转调 PowerShell */
    private fun cmdScriptContent(): String {
        return """
            @echo off
            powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0opencode-editor.ps1" %*
            exit /b %ERRORLEVEL%
        """.trimIndent()
    }

    companion object {
        private const val NOTIFICATION_GROUP_ID = "OpenCode Terminal Tools"
        private const val DEFAULT_EDITOR_OPEN_SHORTCUT = "ctrl+x e"
        private const val LINE_END_SPACE = "\u0005 "
        private const val SETTLE_INPUT_DELAY_MS = 300

        val bridgeDir: Path = Path.of(System.getProperty("java.io.tmpdir"), "opencode-idea-bridge")
        val selectionFile: Path = bridgeDir.resolve("latest-selection.md")
        val powerShellScript: Path = bridgeDir.resolve("opencode-editor.ps1")
        val cmdScript: Path = bridgeDir.resolve("opencode-editor.cmd")

        fun getInstance(project: Project): OpenCodeBridgeService {
            return project.service()
        }

        fun notify(project: Project, message: String, type: NotificationType) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(message, type)
                .notify(project)
        }
    }

    /** 发送结果类型 */
    sealed class BridgeResult {
        data object Success : BridgeResult()
        data object Scheduled : BridgeResult()
        data class Error(val message: String) : BridgeResult()
    }

    /** 终端类型抽象：经典 / 前端（tab 在运行时为 TerminalToolWindowTab 类型） */
    private sealed class TargetTerminal {
        data class Classic(val widget: TerminalWidget) : TargetTerminal()
        data class Frontend(val tab: Any) : TargetTerminal()
    }

    private data class EditorOpenTrigger(val text: String, val isCommand: Boolean)
}
