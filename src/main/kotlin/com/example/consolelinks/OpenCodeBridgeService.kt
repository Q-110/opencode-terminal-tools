package com.example.consolelinks

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
    private var markedTerminal: TargetTerminal? = null

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

    fun canFindTerminal(dataContext: DataContext): Boolean {
        return resolveTargetTerminal(dataContext) != null
    }

    fun sendSelection(payload: String, dataContext: DataContext): BridgeResult {
        return try {
            writeBridgeFiles(payload)
            injectEditorCommand(dataContext)
        } catch (exception: IOException) {
            BridgeResult.Error("写入 OpenCode 桥接文件失败：${exception.message}")
        }
    }

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

    private fun injectEditorCommand(dataContext: DataContext): BridgeResult {
        val terminal = resolveTargetTerminal(dataContext)
            ?: return BridgeResult.Error("没有找到可写入的 OpenCode Terminal。请先在 OpenCode 所在 Terminal 标签页执行 Mark as OpenCode Terminal。")

        return when (terminal) {
            is TargetTerminal.Classic -> injectClassicTerminal(terminal.widget)
            is TargetTerminal.Frontend -> injectFrontendTerminal(terminal.tab)
        }
    }

    private fun injectClassicTerminal(terminal: TerminalWidget): BridgeResult {
        val connector = try {
            terminal.ttyConnector
        } catch (exception: Throwable) {
            return BridgeResult.Error("当前 Terminal 没有暴露可写入的 TTY 连接。")
        } ?: return BridgeResult.Error("当前 Terminal 没有暴露可写入的 TTY 连接。")

        if (!connector.isConnected) {
            markedTerminal = null
            return BridgeResult.Error("已选择的 OpenCode Terminal 已断开连接。")
        }

        return try {
            terminal.requestFocus()
            connector.write(EDITOR_COMMAND)
            BridgeResult.Success
        } catch (exception: IOException) {
            BridgeResult.Error("发送 /editor 到 OpenCode Terminal 失败：${exception.message}")
        }
    }

    private fun injectFrontendTerminal(tab: TerminalToolWindowTab): BridgeResult {
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
                            sendFrontendEditorCommand(view, focusComponent)
                        } catch (exception: Throwable) {
                            notify(project, "发送 /editor 失败：${exception.message}", NotificationType.WARNING)
                        }
                    })
                    focusCallback.doWhenRejected(Runnable {
                        notify(project, "无法聚焦 OpenCode Terminal 输入组件，未发送 /editor。", NotificationType.WARNING)
                    })
                })
        }, true, true)

        return BridgeResult.Scheduled
    }

    private fun sendFrontendEditorCommand(view: TerminalView, focusComponent: JComponent) {
        try {
            sendRawFrontendString(view, EDITOR_COMMAND_TEXT)
        } catch (exception: Throwable) {
            view.sendText(EDITOR_COMMAND_TEXT)
        }
        scheduleFrontendEnter(view, focusComponent)
    }

    private fun scheduleFrontendEnter(view: TerminalView, focusComponent: JComponent) {
        val timer = Timer(100) {
            try {
                IdeFocusManager.getInstance(project)
                    .requestFocusInProject(focusComponent, project)
                    .doWhenDone(Runnable {
                        pressFrontendEnter(view, focusComponent)
                        notify(project, "已发送到 OpenCode", NotificationType.INFORMATION)
                    })
                    .doWhenRejected(Runnable {
                        notify(project, "无法在发送 /editor 后重新聚焦 OpenCode Terminal，未调用 Enter。", NotificationType.WARNING)
                    })
            } catch (exception: Throwable) {
                notify(project, "调用 Enter 失败：${exception.message}", NotificationType.WARNING)
            }
        }
        timer.isRepeats = false
        timer.start()
    }

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

    private fun sendRawFrontendString(view: TerminalView, text: String) {
        val terminalInput = terminalInputFromView(view)
        val sendString = terminalInput.javaClass.getMethod("sendString", String::class.java)
        sendString.invoke(terminalInput, text)
    }

    private fun callFrontendSendEnter(view: TerminalView) {
        val terminalInput = terminalInputFromView(view)
        val sendEnter = terminalInput.javaClass.getMethod("sendEnter")
        sendEnter.invoke(terminalInput)
    }

    private fun terminalInputFromView(view: TerminalView): Any {
        return findField(view.javaClass, "terminalInput")
            ?.let { field ->
                field.isAccessible = true
                field.get(view)
            }
            ?: throw IllegalStateException("无法访问新版 Terminal 输入通道")
    }

    private fun dispatchEnterKeyEvent(component: Component) {
        val now = System.currentTimeMillis()
        component.dispatchEvent(KeyEvent(component, KeyEvent.KEY_PRESSED, now, 0, KeyEvent.VK_ENTER, '\n'))
        component.dispatchEvent(KeyEvent(component, KeyEvent.KEY_RELEASED, now, 0, KeyEvent.VK_ENTER, '\n'))
    }

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

    private fun cmdScriptContent(): String {
        return """
            @echo off
            powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0opencode-editor.ps1" %*
            exit /b %ERRORLEVEL%
        """.trimIndent()
    }

    sealed class BridgeResult {
        data object Success : BridgeResult()
        data object Scheduled : BridgeResult()
        data class Error(val message: String) : BridgeResult()
    }

    private sealed class TargetTerminal {
        data class Classic(val widget: TerminalWidget) : TargetTerminal()
        data class Frontend(val tab: TerminalToolWindowTab) : TargetTerminal()
    }

    companion object {
        private const val NOTIFICATION_GROUP_ID = "Console Links"
        private const val TERMINAL_TOOL_WINDOW_ID = "Terminal"
        private const val EDITOR_COMMAND_TEXT = "/editor"
        private const val EDITOR_COMMAND = "/editor\r"

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
