package io.github.q110.opencodeterminaltools.bridge

import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.ui.TerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

class LegacyReworkedTerminalHelper(
    private val project: Project
) {
    fun createOpenCodeTerminal(tabName: String, workingDirectory: String): TerminalWidget? {
        val manager = TerminalToolWindowManager.getInstance(project)
        val toolWindow = manager.toolWindow ?: return null
        val contentManager = toolWindow.contentManager

        for (engineName in listOf("REWORKED", "NEW_TERMINAL")) {
            val widget = tryCreateTab(manager, contentManager, engineName, tabName, workingDirectory)
            if (widget != null) {
                if (isClassicWidget(widget)) {
                    continue
                }
                contentManager.contents
                    .firstOrNull { TerminalToolWindowManager.findWidgetByContent(it) == widget }
                    ?.let { content ->
                        content.displayName = tabName
                        contentManager.setSelectedContent(content)
                    }
                return widget
            }
        }

        return null
    }

    fun runCommand(widget: TerminalWidget, command: String): OpenCodeBridgeService.BridgeResult {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW_ID)
            ?: return OpenCodeBridgeService.BridgeResult.Error("Terminal tool window was not found.")

        toolWindow.activate(Runnable {
            try {
                widget.requestFocus()
                widget.sendCommandToExecute(command)
                OpenCodeBridgeService.notify(project, "Started OpenCode Terminal (legacy-reworked-best-effort)", NotificationType.INFORMATION)
            } catch (exception: Throwable) {
                try {
                    val connector = widget.ttyConnector
                    if (connector != null && connector.isConnected) {
                        connector.write("$command\r")
                        OpenCodeBridgeService.notify(project, "Started OpenCode Terminal (legacy-reworked-best-effort)", NotificationType.INFORMATION)
                    } else {
                        OpenCodeBridgeService.notify(project, "Failed to run opencode: terminal input is unavailable.", NotificationType.WARNING)
                    }
                } catch (writeException: Throwable) {
                    OpenCodeBridgeService.notify(project, "Failed to run opencode: ${writeException.message}", NotificationType.WARNING)
                }
            }
        }, true, true)

        return OpenCodeBridgeService.BridgeResult.Scheduled
    }

    private fun tryCreateTab(
        manager: TerminalToolWindowManager,
        contentManager: Any,
        engineName: String,
        tabName: String,
        workingDirectory: String
    ): TerminalWidget? {
        val engine = enumValue("org.jetbrains.plugins.terminal.TerminalEngine", engineName) ?: return null
        val state = terminalTabState(tabName, workingDirectory) ?: return null

        return tryCreate2025xTab(manager, engine, state, contentManager)
            ?: tryCreate20252Tab(manager, engine, state)
    }

    private fun tryCreate2025xTab(
        manager: TerminalToolWindowManager,
        engine: Any,
        state: Any,
        contentManager: Any
    ): TerminalWidget? {
        return try {
            val method = manager.javaClass.methods.firstOrNull { method ->
                method.name == "createNewTab" &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0].name == "org.jetbrains.plugins.terminal.TerminalEngine" &&
                    method.parameterTypes[1].name == "org.jetbrains.plugins.terminal.TerminalTabState" &&
                    method.parameterTypes[2].name == "com.intellij.ui.content.ContentManager"
            } ?: return null
            method.invoke(manager, engine, state, contentManager) as? TerminalWidget
        } catch (_: Throwable) {
            null
        }
    }

    private fun isClassicWidget(widget: TerminalWidget): Boolean {
        val className = widget.javaClass.name
        return className.contains("ShellTerminalWidget") || className.contains("JediTerm")
    }

    private fun tryCreate20252Tab(
        manager: TerminalToolWindowManager,
        engine: Any,
        state: Any
    ): TerminalWidget? {
        return try {
            val fusInfo = terminalStartupFusInfo() ?: return null
            val method = manager.javaClass.methods.firstOrNull { method ->
                method.name == "createNewTab" &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0].name == "org.jetbrains.plugins.terminal.TerminalEngine" &&
                    method.parameterTypes[1].name == "org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo" &&
                    method.parameterTypes[2].name == "org.jetbrains.plugins.terminal.TerminalTabState"
            } ?: return null
            method.invoke(manager, engine, fusInfo, state) as? TerminalWidget
        } catch (_: Throwable) {
            null
        }
    }

    private fun terminalTabState(tabName: String, workingDirectory: String): Any? {
        return try {
            val stateClass = Class.forName("org.jetbrains.plugins.terminal.TerminalTabState")
            val state = stateClass.getConstructor().newInstance()
            stateClass.getField("myTabName").set(state, tabName)
            stateClass.getField("myIsUserDefinedTabTitle").setBoolean(state, true)
            stateClass.getField("myWorkingDirectory").set(state, workingDirectory)
            state
        } catch (_: Throwable) {
            null
        }
    }

    private fun terminalStartupFusInfo(): Any? {
        return try {
            val openingWay = enumValue("org.jetbrains.plugins.terminal.fus.TerminalOpeningWay", "OPEN_NEW_TAB")
                ?: return null
            val fusInfoClass = Class.forName("org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo")
            fusInfoClass.constructors
                .firstOrNull { constructor -> constructor.parameterTypes.size == 1 }
                ?.newInstance(openingWay)
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun enumValue(className: String, valueName: String): Any? {
        return try {
            val enumClass = Class.forName(className) as Class<out Enum<*>>
            java.lang.Enum.valueOf(enumClass, valueName)
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private const val TERMINAL_TOOL_WINDOW_ID = "Terminal"
    }
}
