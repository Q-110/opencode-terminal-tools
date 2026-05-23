// OpenCode 桥接核心服务 — 选区写入桥接文件 → 触发 OpenCode editor_open 快捷键
package io.github.q110.opencodeterminaltools.bridge

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.ui.TerminalWidget
import io.github.q110.opencodeterminaltools.settings.OpenCodeTerminalToolsSettings
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.awt.Component
import java.awt.Robot
import java.awt.event.KeyEvent
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.swing.JComponent
import javax.swing.Timer

@Service(Service.Level.PROJECT)
class OpenCodeBridgeService(
    private val project: Project
) {
    /** 用户手动标记的目标终端（优先使用） */
    private var markedTerminal: TargetTerminal? = null

    /** 标记当前终端：右键菜单 → 当前终端 → 选中标签页 → 唯一可用终端 */
    fun markTerminal(dataContext: DataContext): Boolean {
        val terminal = classicTerminalFromDataContext(dataContext)?.let { TargetTerminal.Classic(it) }
            ?: selectedFrontendTerminal()?.let { TargetTerminal.Frontend(it) }
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
        Files.writeString(
            selectionFile,
            payload,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
        Files.writeString(
            powerShellScript,
            powerShellScriptContent(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
        Files.writeString(
            cmdScript,
            cmdScriptContent(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }

    /** 阶段二：找到目标终端并触发 editor_open */
    private fun injectEditorCommand(dataContext: DataContext, settleAtLineEnd: Boolean): BridgeResult {
        val terminal = resolveTargetTerminal(dataContext)
            ?: return BridgeResult.Error("没有找到可写入的 OpenCode Terminal。请先在 OpenCode 所在 Terminal 标签页执行 Mark as OpenCode Terminal。")

        return when (terminal) {
            is TargetTerminal.Classic -> injectClassicTerminal(terminal.widget, settleAtLineEnd)
            is TargetTerminal.Frontend -> injectFrontendTerminal(terminal.tab, settleAtLineEnd)
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
                scheduleClassicLineEndSpace {
                    connector.write(LINE_END_SPACE)
                }
            }
            BridgeResult.Success
        } catch (exception: IOException) {
            BridgeResult.Error("发送 OpenCode editor_open 到 Terminal 失败：${exception.message}")
        }
    }

    /** 经典终端上行尾空格延时 300ms（等待 OpenCode 处理完输入） */
    private fun scheduleClassicLineEndSpace(writeLineEndSpace: () -> Unit) {
        val timer = Timer(SETTLE_INPUT_DELAY_MS) {
            try {
                writeLineEndSpace()
            } catch (exception: Throwable) {
                notify(project, "发送 OpenCode 行尾空格失败：${exception.message}", NotificationType.WARNING)
            }
        }
        timer.isRepeats = false
        timer.start()
    }

    /** 通过新版 Terminal API 激活标签页、聚焦、发送快捷键 */
    private fun injectFrontendTerminal(tab: TerminalToolWindowTab, settleAtLineEnd: Boolean): BridgeResult {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW_ID)
            ?: return BridgeResult.Error("没有找到 Terminal 工具窗口。")

        toolWindow.activate(Runnable {
            val view = tab.view
            toolWindow.contentManager.setSelectedContentCB(tab.content, true, true)
                .doWhenProcessed(Runnable {
                    val focusComponent = view.preferredFocusableComponent
                    val focusCallback = IdeFocusManager.getInstance(project)
                        .requestFocusInProject(focusComponent, project)
                    focusCallback.doWhenDone(Runnable {
                        try {
                            sendFrontendEditorCommand(view, focusComponent, settleAtLineEnd)
                        } catch (exception: Throwable) {
                            notify(project, "发送 OpenCode editor_open 失败：${exception.message}", NotificationType.WARNING)
                        }
                    })
                    focusCallback.doWhenRejected(Runnable {
                        notify(project, "无法聚焦 OpenCode Terminal 输入组件，未发送 editor_open。", NotificationType.WARNING)
                    })
                })
        }, true, true)

        return BridgeResult.Scheduled
    }

    /** 前端终端聚焦后：反射调用 sendString → 如果是命令则延时发送 Enter */
    private fun sendFrontendEditorCommand(view: TerminalView, focusComponent: JComponent, settleAtLineEnd: Boolean) {
        val trigger = editorOpenTrigger()
        try {
            sendRawFrontendString(view, trigger.text)
        } catch (exception: Throwable) {
            if (trigger.isCommand) {
                view.sendText(trigger.text.trimEnd('\r'))
            } else {
                throw exception
            }
        }
        if (trigger.isCommand) {
            scheduleFrontendEnter(view, focusComponent, settleAtLineEnd)
        } else if (settleAtLineEnd) {
            scheduleFrontendLineEndSpace(view, focusComponent, true)
        } else {
            notify(project, "已发送到 OpenCode", NotificationType.INFORMATION)
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

    /** 命令模式：延时 100ms 后三层次 Fallback 发送 Enter */
    private fun scheduleFrontendEnter(view: TerminalView, focusComponent: JComponent, settleAtLineEnd: Boolean) {
        val timer = Timer(100) {
            try {
                IdeFocusManager.getInstance(project)
                    .requestFocusInProject(focusComponent, project)
                    .doWhenDone(Runnable {
                        pressFrontendEnter(view, focusComponent)
                        if (settleAtLineEnd) {
                            scheduleFrontendLineEndSpace(view, focusComponent, true)
                        } else {
                            notify(project, "已发送到 OpenCode", NotificationType.INFORMATION)
                        }
                    })
                    .doWhenRejected(Runnable {
                        notify(project, "无法在发送 editor_open 后重新聚焦 OpenCode Terminal，未调用 Enter。", NotificationType.WARNING)
                    })
            } catch (exception: Throwable) {
                notify(project, "调用 Enter 失败：${exception.message}", NotificationType.WARNING)
            }
        }
        timer.isRepeats = false
        timer.start()
    }

    /** 延时 300ms 后发送行尾空格（结束 OpenCode @路径补全状态） */
    private fun scheduleFrontendLineEndSpace(view: TerminalView, focusComponent: JComponent, notifyAfter: Boolean) {
        val timer = Timer(SETTLE_INPUT_DELAY_MS) {
            try {
                IdeFocusManager.getInstance(project)
                    .requestFocusInProject(focusComponent, project)
                    .doWhenDone(Runnable {
                        sendFrontendLineEndSpace(view)
                        if (notifyAfter) {
                            notify(project, "已发送到 OpenCode", NotificationType.INFORMATION)
                        }
                    })
                    .doWhenRejected(Runnable {
                        notify(project, "无法聚焦 OpenCode Terminal 输入组件，未发送行尾空格。", NotificationType.WARNING)
                    })
            } catch (exception: Throwable) {
                notify(project, "发送 OpenCode 行尾空格失败：${exception.message}", NotificationType.WARNING)
            }
        }
        timer.isRepeats = false
        timer.start()
    }

    /** 三层次 Enter Fallback：Robot API → 反射 sendEnter → KeyEvent dispatch */
    private fun pressFrontendEnter(view: TerminalView, focusComponent: JComponent) {
        try {
            pressRobotEnter()
            return
        } catch (_: Throwable) {
        }
        try {
            callFrontendSendEnter(view)
            return
        } catch (_: Throwable) {
        }
        dispatchEnterKeyEvent(focusComponent)
    }

    private fun pressRobotEnter() {
        val robot = Robot()
        robot.keyPress(KeyEvent.VK_ENTER)
        robot.keyRelease(KeyEvent.VK_ENTER)
    }

    /** 反射调用 terminalInput.sendString() */
    private fun sendRawFrontendString(view: TerminalView, text: String) {
        val terminalInput = terminalInputFromView(view)
        val sendString = terminalInput.javaClass.getMethod("sendString", String::class.java)
        sendString.invoke(terminalInput, text)
    }

    /** 发送行尾空格：优先反射 sendString → 退化为 view.sendText */
    private fun sendFrontendLineEndSpace(view: TerminalView) {
        try {
            sendRawFrontendString(view, LINE_END_SPACE)
        } catch (_: Throwable) {
            view.sendText(LINE_END_SPACE)
        }
    }

    /** 反射调用 terminalInput.sendEnter() */
    private fun callFrontendSendEnter(view: TerminalView) {
        val terminalInput = terminalInputFromView(view)
        val sendEnter = terminalInput.javaClass.getMethod("sendEnter")
        sendEnter.invoke(terminalInput)
    }

    /** 从 TerminalView 反射获取 internal 字段 terminalInput */
    private fun terminalInputFromView(view: TerminalView): Any {
        return findField(view.javaClass, "terminalInput")
            ?.let { field ->
                field.isAccessible = true
                field.get(view)
            }
            ?: throw IllegalStateException("无法访问新版 Terminal 输入通道")
    }

    /** 直接向组件派发 KeyEvent（最末回退） */
    private fun dispatchEnterKeyEvent(component: Component) {
        val now = System.currentTimeMillis()
        component.dispatchEvent(KeyEvent(component, KeyEvent.KEY_PRESSED, now, 0, KeyEvent.VK_ENTER, '\n'))
        component.dispatchEvent(KeyEvent(component, KeyEvent.KEY_RELEASED, now, 0, KeyEvent.VK_ENTER, '\n'))
    }

    /** 沿类继承链向上查找字段（处理 Java 内部类的继承结构） */
    private fun findField(type: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = type
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (exception: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    /** 终端发现优先级：已标记 → DataContext → 选中标签页 → 唯一可用 */
    private fun resolveTargetTerminal(dataContext: DataContext): TargetTerminal? {
        val marked = markedTerminal
        if (marked != null) {
            if (isUsable(marked)) {
                return marked
            }
            markedTerminal = null
        }

        classicTerminalFromDataContext(dataContext)?.let {
            val target = TargetTerminal.Classic(it)
            if (isUsable(target)) return target
        }

        selectedTerminal()?.let {
            if (isUsable(it)) return it
        }

        return singleUsableTerminal()
    }

    private fun classicTerminalFromDataContext(dataContext: DataContext): TerminalWidget? {
        return JBTerminalWidget.TERMINAL_DATA_KEY.getData(dataContext)?.asNewWidget()
    }

    /** 优先前端终端 → 经典终端 */
    private fun selectedTerminal(): TargetTerminal? {
        return selectedFrontendTerminal()?.let { TargetTerminal.Frontend(it) }
            ?: selectedClassicTerminal()?.let { TargetTerminal.Classic(it) }
    }

    private fun selectedClassicTerminal(): TerminalWidget? {
        val toolWindow = TerminalToolWindowManager.getInstance(project).toolWindow ?: return null
        val selectedContent = toolWindow.contentManager.selectedContent ?: return null
        return TerminalToolWindowManager.findWidgetByContent(selectedContent)
    }

    private fun selectedFrontendTerminal(): TerminalToolWindowTab? {
        val selectedContent = ToolWindowManager.getInstance(project)
            .getToolWindow(TERMINAL_TOOL_WINDOW_ID)
            ?.contentManager
            ?.selectedContent
            ?: return null
        return TerminalToolWindowTabsManager.getInstance(project)
            .tabs
            .firstOrNull { it.content == selectedContent }
    }

    /** 唯一可用终端（前端 + 经典合并列表） */
    private fun singleUsableTerminal(): TargetTerminal? {
        val frontendTerminals = TerminalToolWindowTabsManager.getInstance(project)
            .tabs
            .map { TargetTerminal.Frontend(it) }
            .filter { isUsable(it) }
        val classicTerminals = TerminalToolWindowManager.getInstance(project)
            .terminalWidgets
            .map { TargetTerminal.Classic(it) }
            .filter { isUsable(it) }
        val terminals = frontendTerminals + classicTerminals
        return terminals.singleOrNull()
    }

    /** 检查终端是否可用：经典终端检查 TTY 连接，前端终端检查 tab 仍存在 */
    private fun isUsable(terminal: TargetTerminal): Boolean {
        return when (terminal) {
            is TargetTerminal.Classic -> {
                try {
                    terminal.widget.ttyConnector?.isConnected == true
                } catch (exception: Throwable) {
                    false
                }
            }
            is TargetTerminal.Frontend -> TerminalToolWindowTabsManager.getInstance(project)
                .tabs
                .any { it == terminal.tab }
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

    /** 发送结果类型 */
    sealed class BridgeResult {
        data object Success : BridgeResult()
        data object Scheduled : BridgeResult()
        data class Error(val message: String) : BridgeResult()
    }

    /** 终端类型抽象：经典 / 前端 */
    private sealed class TargetTerminal {
        data class Classic(val widget: TerminalWidget) : TargetTerminal()
        data class Frontend(val tab: TerminalToolWindowTab) : TargetTerminal()
    }

    private data class EditorOpenTrigger(val text: String, val isCommand: Boolean)

    companion object {
        private const val NOTIFICATION_GROUP_ID = "OpenCode Terminal Tools"
        private const val TERMINAL_TOOL_WINDOW_ID = "Terminal"
        private const val DEFAULT_EDITOR_OPEN_SHORTCUT = "ctrl+x e"
        /** 行尾空格使用 \u0005（Ctrl+E 的终端编码）加空格 */
        private const val LINE_END_SPACE = "\u0005 "
        /** 等待 OpenCode 处理输入后再发行尾空格 */
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
}
