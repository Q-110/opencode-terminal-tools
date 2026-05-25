// OpenCode 桥接核心服务 — 选区写入桥接文件 → 触发 OpenCode editor_open 快捷键
package io.github.q110.opencodeterminaltools.bridge

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import io.github.q110.opencodeterminaltools.filter.displayPath
import io.github.q110.opencodeterminaltools.settings.OpenCodeTerminalToolsSettings
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean
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

    private val legacyReworkedTerminalHelper = LegacyReworkedTerminalHelper(project)
    private val openCodeTerminalStartInProgress = AtomicBoolean(false)

    /** 标记当前终端：右键菜单 → 当前终端 → 选中标签页 → 唯一可用终端 */
    fun markTerminal(dataContext: DataContext): Boolean {
        val terminal = classicTerminalFromDataContext(dataContext)?.let { TargetTerminal.Classic(it) }
            ?: selectedClassicTerminal()?.let { TargetTerminal.Classic(it) }
            ?: selectedTerminal()
            ?: singleUsableTerminal()
            ?: return false
        markedTerminal = terminal
        project.service<OpenCodeTerminalDropService>().refreshDropTarget()
        return true
    }

    /** 检查是否有可用终端 */
    fun canFindTerminal(dataContext: DataContext): Boolean {
        return resolveTargetTerminal(dataContext) != null
    }

    /** 当前拖拽目标必须是已选中且已标记的 OpenCode 终端 */
    fun isActiveMarkedTerminalContent(content: Content): Boolean {
        val toolWindow = TerminalToolWindowManager.getInstance(project).toolWindow ?: return false
        if (toolWindow.contentManager.selectedContent !== content) {
            return false
        }

        return when (val marked = markedUsableTerminal()) {
            is TargetTerminal.Classic -> TerminalToolWindowManager.findWidgetByContent(content) == marked.widget
            is TargetTerminal.Frontend -> frontendHelper?.isContentOf(marked.tab, content) == true
            null -> false
        }
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

    /** 拖拽路径合并为一次 editor_open，避免多次触发造成卡顿 */
    fun sendDroppedPaths(files: List<VirtualFile>): BridgeResult {
        val payload = files.filter { it.isValid }
            .joinToString(separator = "\n") { pathPayload(it) }
        if (payload.isBlank()) {
            return BridgeResult.Error("没有找到要发送的文件或文件夹。")
        }
        if (markedUsableTerminal() == null) {
            return BridgeResult.Error("请先在 OpenCode 所在 Terminal 标签页执行 Mark as OpenCode Terminal。")
        }

        return try {
            writeBridgeFiles(payload)
            injectMarkedEditorCommand(settleAtLineEnd = true)
        } catch (exception: IOException) {
            BridgeResult.Error("写入 OpenCode 桥接文件失败：${exception.message}")
        }
    }

    /** 创建新的 OpenCode terminal，并在 shell 启动前注入桥接 EDITOR 环境变量 */
    fun startOpenCodeTerminal(): BridgeResult {
        return try {
            ensureBridgeScripts()
            if (!openCodeTerminalStartInProgress.compareAndSet(false, true)) {
                return BridgeResult.Scheduled
            }
            scheduleOpenCodeTerminalStart()
            BridgeResult.Scheduled
        } catch (exception: IOException) {
            BridgeResult.Error("Failed to write OpenCode bridge scripts: ${exception.message}")
        }
    }

    private fun scheduleOpenCodeTerminalStart() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                openCodeTerminalStartInProgress.set(false)
                return@invokeLater
            }

            val terminalToolWindowManager = TerminalToolWindowManager.getInstance(project)
            val toolWindow = terminalToolWindow(terminalToolWindowManager)
            if (toolWindow == null) {
                openCodeTerminalStartInProgress.set(false)
                notify(project, "Terminal tool window was not found.", NotificationType.WARNING)
                return@invokeLater
            }

            toolWindow.activate(Runnable {
                ApplicationManager.getApplication().invokeLater {
                    try {
                        val tabName = nextOpenCodeTabName()
                        val workingDirectory = openCodeWorkingDirectory()
                        val result = startFrontendOpenCodeTerminal(tabName, workingDirectory)
                            ?: startLegacyReworkedOpenCodeTerminal(tabName, workingDirectory)
                            ?: run {
                                notifyLegacyReworkedFallbackIfNeeded()
                                startClassicOpenCodeTerminal(tabName, workingDirectory)
                            }
                        if (result is BridgeResult.Error) {
                            notify(project, result.message, NotificationType.WARNING)
                        }
                    } finally {
                        openCodeTerminalStartInProgress.set(false)
                    }
                }
            }, true, true)
        }
    }

    private fun startFrontendOpenCodeTerminal(tabName: String, workingDirectory: String): BridgeResult? {
        val helper = frontendHelper ?: return null
        return try {
            val tab = helper.createOpenCodeTerminal(tabName, workingDirectory)
            markedTerminal = TargetTerminal.Frontend(tab)
            project.service<OpenCodeTerminalDropService>().refreshDropTarget()
            helper.runCommand(tab, openCodeStartupCommand())
        } catch (exception: Throwable) {
            notify(project, "New Terminal is unavailable, falling back to Classic Terminal: ${exception.message}", NotificationType.WARNING)
            null
        }
    }

    private fun startLegacyReworkedOpenCodeTerminal(tabName: String, workingDirectory: String): BridgeResult? {
        return try {
            val widget = legacyReworkedTerminalHelper.createOpenCodeTerminal(tabName, workingDirectory)
                ?: return null
            markedTerminal = TargetTerminal.Classic(widget)
            project.service<OpenCodeTerminalDropService>().refreshDropTarget()
            legacyReworkedTerminalHelper.runCommand(widget, openCodeStartupCommand())
        } catch (exception: Throwable) {
            notify(project, "Reworked Terminal is unavailable, falling back to Classic Terminal: ${exception.message}", NotificationType.WARNING)
            null
        }
    }

    private fun startClassicOpenCodeTerminal(tabName: String, workingDirectory: String): BridgeResult {
        val terminalToolWindowManager = TerminalToolWindowManager.getInstance(project)
        val toolWindow = terminalToolWindow(terminalToolWindowManager)
            ?: return BridgeResult.Error("Terminal tool window was not found.")
        val startupOptions = ShellStartupOptions.Builder()
            .workingDirectory(workingDirectory)
            .envVariables(mapOf(EDITOR_ENV_NAME to cmdScript.toString()))
            .build()
        val startupDisposable: Disposable = Disposer.newDisposable("OpenCode Terminal startup")
        val widget = try {
            terminalToolWindowManager.terminalRunner.startShellTerminalWidget(startupDisposable, startupOptions, true)
        } catch (exception: Throwable) {
            Disposer.dispose(startupDisposable)
            return BridgeResult.Error("Failed to create OpenCode Terminal: ${exception.message}")
        }

        val content = terminalToolWindowManager.newTab(toolWindow, widget)
        content.displayName = tabName
        markedTerminal = TargetTerminal.Classic(widget)
        project.service<OpenCodeTerminalDropService>().refreshDropTarget()
        toolWindow.activate(Runnable {
            try {
                ShellTerminalWidget.toShellJediTermWidgetOrThrow(widget).executeCommand(OPENCODE_COMMAND)
                notify(project, "Started OpenCode Terminal (classic-fallback)", NotificationType.INFORMATION)
            } catch (exception: Throwable) {
                notify(project, "Failed to run opencode: ${exception.message}", NotificationType.WARNING)
            }
        }, true, true)
        return BridgeResult.Scheduled
    }

    private fun writeBridgeFiles(payload: String) {
        ensureBridgeScripts()
        Files.writeString(selectionFile, payload, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
    }

    private fun pathPayload(file: VirtualFile): String {
        return "@${displayPath(project, file)}"
    }

    /** 在 %TEMP%\opencode-idea-bridge\ 下写入桥接脚本，不覆盖 latest-selection.md */
    private fun ensureBridgeScripts() {
        Files.createDirectories(bridgeDir)
        Files.writeString(powerShellScript, powerShellScriptContent(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        Files.writeString(cmdScript, cmdScriptContent(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
    }

    private fun openCodeWorkingDirectory(): String {
        return project.basePath ?: System.getProperty("user.home")
    }

    private fun openCodeStartupCommand(): String {
        return if (SystemInfo.isWindows) {
            "\$env:$EDITOR_ENV_NAME='${powerShellSingleQuoted(cmdScript.toString())}'; $OPENCODE_COMMAND"
        } else {
            "export $EDITOR_ENV_NAME='${shSingleQuoted(cmdScript.toString())}'; $OPENCODE_COMMAND"
        }
    }

    private fun nextOpenCodeTabName(): String {
        val existingNames = terminalNames()
        val pattern = Regex("""^${Regex.escape(OPENCODE_TAB_NAME)} \((\d+)\)$""")
        val maxIndex = existingNames.fold(0) { max, name ->
            when {
                name == OPENCODE_TAB_NAME -> maxOf(max, 1)
                else -> maxOf(max, pattern.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0)
            }
        }
        return if (maxIndex == 0) OPENCODE_TAB_NAME else "$OPENCODE_TAB_NAME (${maxIndex + 1})"
    }

    private fun terminalNames(): List<String> {
        val frontendNames = frontendHelper?.allTerminalNames().orEmpty()
        val classicNames = TerminalToolWindowManager.getInstance(project)
            .toolWindow
            ?.contentManager
            ?.contents
            ?.mapNotNull { it.displayName }
            .orEmpty()
        return frontendNames + classicNames
    }

    private fun powerShellSingleQuoted(value: String): String {
        return value.replace("'", "''")
    }

    private fun shSingleQuoted(value: String): String {
        return value.replace("'", "'\"'\"'")
    }

    private fun terminalToolWindow(manager: TerminalToolWindowManager): ToolWindow? {
        manager.toolWindow?.let { return it }
        return try {
            val method = manager.javaClass.getDeclaredMethod("getOrInitToolWindow")
            method.isAccessible = true
            method.invoke(manager) as? ToolWindow
        } catch (_: Throwable) {
            null
        }
    }

    private fun notifyLegacyReworkedFallbackIfNeeded() {
        when (ideBaselineVersion()) {
            251 -> notify(
                project,
                "Using Classic Terminal (classic-fallback). IntelliJ IDEA 2025.1 defaults to Classic; Reworked 2025 is Beta and has no stable public startup API for plugins.",
                NotificationType.WARNING
            )
            252 -> notify(
                project,
                "Using Classic Terminal (classic-fallback). Reworked startup in 2025.2 uses best-effort internal APIs; the reliable public startup API is available in 2025.3+.",
                NotificationType.WARNING
            )
        }
    }

    private fun ideBaselineVersion(): Int {
        return ApplicationInfo.getInstance().build.baselineVersion
    }

    /** 拖拽发送只使用已标记终端，不走当前终端或唯一终端回退 */
    private fun injectMarkedEditorCommand(settleAtLineEnd: Boolean): BridgeResult {
        val terminal = markedUsableTerminal()
            ?: return BridgeResult.Error("请先在 OpenCode 所在 Terminal 标签页执行 Mark as OpenCode Terminal。")

        return injectTerminal(terminal, settleAtLineEnd)
    }

    /** 阶段二：找到目标终端并触发 editor_open */
    private fun injectEditorCommand(dataContext: DataContext, settleAtLineEnd: Boolean): BridgeResult {
        val terminal = resolveTargetTerminal(dataContext)
            ?: return BridgeResult.Error("没有找到可写入的 OpenCode Terminal。请先在 OpenCode 所在 Terminal 标签页执行 Mark as OpenCode Terminal。")

        return injectTerminal(terminal, settleAtLineEnd)
    }

    private fun injectTerminal(terminal: TargetTerminal, settleAtLineEnd: Boolean): BridgeResult {
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
        markedUsableTerminal()?.let { return it }
        classicTerminalFromDataContext(dataContext)?.let {
            val target = TargetTerminal.Classic(it)
            if (isUsable(target)) return target
        }
        selectedTerminal()?.let { if (isUsable(it)) return it }
        return singleUsableTerminal()
    }

    private fun markedUsableTerminal(): TargetTerminal? {
        val marked = markedTerminal ?: return null
        if (isUsable(marked)) {
            return marked
        }
        markedTerminal = null
        return null
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
            Remove-Item -LiteralPath ${'$'}SelectionFile -Force -ErrorAction SilentlyContinue

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
        private const val OPENCODE_COMMAND = "opencode"
        private const val OPENCODE_TAB_NAME = "OpenCode"
        private const val EDITOR_ENV_NAME = "EDITOR"
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
