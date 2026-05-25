package io.github.q110.aiterminaltools.bridge

import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.ui.content.Content
import io.github.q110.aiterminaltools.bridge.AiTerminalBridgeService.BridgeResult
import java.awt.Component
import java.awt.Robot
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.Timer

class FrontendTerminalHelper(
    private val project: Project
) {
    private val tabsManager = TerminalToolWindowTabsManager.getInstance(project)

    fun selectedTerminal(): Any? {
        val selectedContent = ToolWindowManager.getInstance(project)
            .getToolWindow(TERMINAL_TOOL_WINDOW_ID)
            ?.contentManager?.selectedContent ?: return null
        return tabs().firstOrNull { contentOf(it) == selectedContent }
    }

    fun allTerminals(): List<Any> = tabs()

    fun allTerminalNames(): List<String> {
        return tabs().mapNotNull { contentOf(it)?.displayName }
    }

    fun createAiTerminal(tabName: String, workingDirectory: String): Any {
        return tabsManager.createTabBuilder()
            .workingDirectory(workingDirectory)
            .tabName(tabName)
            .requestFocus(true)
            .deferSessionStartUntilUiShown(false)
            .createTab()
    }

    fun runCommand(
        tab: Any,
        command: String,
        successMessage: String,
        failurePrefix: String,
        onCommandSent: () -> Unit = {}
    ): BridgeResult {
        val content = contentOf(tab)
            ?: return BridgeResult.Error("Invalid frontend terminal tab.")
        val view = viewOf(tab)
            ?: return BridgeResult.Error("Invalid frontend terminal view.")
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW_ID)
            ?: return BridgeResult.Error("Terminal tool window was not found.")

        toolWindow.activate(Runnable {
            toolWindow.contentManager.setSelectedContentCB(content, true, true)
                .doWhenProcessed(Runnable {
                    val focusComponent = preferredFocusableComponent(view)
                    if (focusComponent == null) {
                        AiTerminalBridgeService.notify(project, "Cannot focus the AI terminal input component.", NotificationType.WARNING)
                        return@Runnable
                    }
                    IdeFocusManager.getInstance(project)
                        .requestFocusInProject(focusComponent, project)
                        .doWhenDone(Runnable {
                            try {
                                sendText(view, command)
                                scheduleEnter(view, focusComponent, false, successMessage, onCommandSent)
                            } catch (exception: Throwable) {
                                AiTerminalBridgeService.notify(project, "$failurePrefix: ${exception.message}", NotificationType.WARNING)
                            }
                        })
                        .doWhenRejected(Runnable {
                            AiTerminalBridgeService.notify(project, "Cannot focus the AI terminal input component.", NotificationType.WARNING)
                        })
                })
        }, true, true)

        return BridgeResult.Scheduled
    }

    fun isTabExists(tab: Any): Boolean {
        return tabs().any { it == tab }
    }

    fun isContentOf(tab: Any, content: Content): Boolean {
        return contentOf(tab) == content
    }

    fun injectDirectInput(tab: Any, payload: String, settleAtLineEnd: Boolean): BridgeResult {
        val content = contentOf(tab)
            ?: return BridgeResult.Error("Invalid frontend terminal tab.")
        val view = viewOf(tab)
            ?: return BridgeResult.Error("Invalid frontend terminal view.")
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW_ID)
            ?: return BridgeResult.Error("Terminal tool window was not found.")

        toolWindow.activate(Runnable {
            toolWindow.contentManager.setSelectedContentCB(content, true, true)
                .doWhenProcessed(Runnable {
                    val focusComponent = preferredFocusableComponent(view)
                    if (focusComponent == null) {
                        AiTerminalBridgeService.notify(project, "Cannot focus the AI terminal input component.", NotificationType.WARNING)
                        return@Runnable
                    }
                    val focusCallback = IdeFocusManager.getInstance(project)
                        .requestFocusInProject(focusComponent, project)
                    focusCallback.doWhenDone(Runnable {
                        try {
                            sendDirectInput(view, focusComponent, payload, settleAtLineEnd)
                        } catch (exception: Throwable) {
                            AiTerminalBridgeService.notify(project, "发送 AI Terminal 输入失败：${exception.message}", NotificationType.WARNING)
                        }
                    })
                    focusCallback.doWhenRejected(Runnable {
                        AiTerminalBridgeService.notify(project, "Cannot focus the AI terminal input component.", NotificationType.WARNING)
                    })
                })
        }, true, true)

        return BridgeResult.Scheduled
    }

    private fun tabs(): List<Any> {
        return tabsManager.tabs
    }

    private fun sendDirectInput(view: Any, focusComponent: JComponent, payload: String, settleAtLineEnd: Boolean) {
        try {
            sendRawString(view, payload)
        } catch (_: Throwable) {
            sendText(view, payload)
        }
        if (settleAtLineEnd) {
            scheduleLineEndSpace(view, focusComponent, true)
        } else {
            AiTerminalBridgeService.notify(project, "已发送到 AI Terminal", NotificationType.INFORMATION)
        }
    }

    private fun scheduleEnter(
        view: Any,
        focusComponent: JComponent,
        settleAtLineEnd: Boolean,
        successMessage: String = "已发送到 AI Terminal",
        onEnterSent: () -> Unit = {}
    ) {
        val timer = Timer(100) {
            try {
                IdeFocusManager.getInstance(project)
                    .requestFocusInProject(focusComponent, project)
                    .doWhenDone(Runnable {
                        pressEnter(view, focusComponent)
                        onEnterSent()
                        if (settleAtLineEnd) {
                            scheduleLineEndSpace(view, focusComponent, true)
                        } else {
                            AiTerminalBridgeService.notify(project, successMessage, NotificationType.INFORMATION)
                        }
                    })
                    .doWhenRejected(Runnable {
                        AiTerminalBridgeService.notify(project, "Cannot refocus the AI terminal.", NotificationType.WARNING)
                    })
            } catch (exception: Throwable) {
                AiTerminalBridgeService.notify(project, "Failed to invoke Enter: ${exception.message}", NotificationType.WARNING)
            }
        }
        timer.isRepeats = false
        timer.start()
    }

    private fun scheduleLineEndSpace(view: Any, focusComponent: JComponent, notifyAfter: Boolean) {
        val timer = Timer(300) {
            try {
                IdeFocusManager.getInstance(project)
                    .requestFocusInProject(focusComponent, project)
                    .doWhenDone(Runnable {
                        sendLineEndSpace(view)
                        if (notifyAfter) {
                            AiTerminalBridgeService.notify(project, "已发送到 AI Terminal", NotificationType.INFORMATION)
                        }
                    })
                    .doWhenRejected(Runnable {
                        AiTerminalBridgeService.notify(project, "Cannot focus the AI terminal input component.", NotificationType.WARNING)
                    })
            } catch (exception: Throwable) {
                AiTerminalBridgeService.notify(project, "发送 AI Terminal 行尾空格失败：${exception.message}", NotificationType.WARNING)
            }
        }
        timer.isRepeats = false
        timer.start()
    }

    private fun pressEnter(view: Any, focusComponent: JComponent) {
        try {
            val robot = Robot()
            robot.keyPress(KeyEvent.VK_ENTER)
            robot.keyRelease(KeyEvent.VK_ENTER)
            return
        } catch (_: Throwable) {
        }
        try {
            callSendEnter(view)
            return
        } catch (_: Throwable) {
        }
        dispatchEnterKeyEvent(focusComponent)
    }

    private fun sendRawString(view: Any, text: String) {
        val input = terminalInput(view)
        input.javaClass.getMethod("sendString", String::class.java).invoke(input, text)
    }

    private fun sendLineEndSpace(view: Any) {
        try {
            sendRawString(view, LINE_END_SPACE)
        } catch (_: Throwable) {
            sendText(view, LINE_END_SPACE)
        }
    }

    private fun callSendEnter(view: Any) {
        val input = terminalInput(view)
        input.javaClass.getMethod("sendEnter").invoke(input)
    }

    private fun sendText(view: Any, text: String) {
        view.javaClass.getMethod("sendText", String::class.java).invoke(view, text)
    }

    private fun terminalInput(view: Any): Any {
        return findField(view.javaClass, "terminalInput")
            ?.let { field -> field.also { it.isAccessible = true }.get(view) }
            ?: throw IllegalStateException("Cannot access the frontend terminal input channel.")
    }

    private fun contentOf(tab: Any): Content? {
        return tab.javaClass.getMethod("getContent").invoke(tab) as? Content
    }

    private fun viewOf(tab: Any): Any? {
        return tab.javaClass.getMethod("getView").invoke(tab)
    }

    private fun preferredFocusableComponent(view: Any): JComponent? {
        return view.javaClass.getMethod("getPreferredFocusableComponent").invoke(view) as? JComponent
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
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    companion object {
        private const val TERMINAL_TOOL_WINDOW_ID = "Terminal"
        private const val LINE_END_SPACE = "\u0005 "
    }
}
