package io.github.q110.opencodeterminaltools.bridge

import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.ui.content.Content
import io.github.q110.opencodeterminaltools.bridge.OpenCodeBridgeService.BridgeResult
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

    fun createOpenCodeTerminal(tabName: String, workingDirectory: String): Any {
        return tabsManager.createTabBuilder()
            .workingDirectory(workingDirectory)
            .tabName(tabName)
            .requestFocus(true)
            .deferSessionStartUntilUiShown(false)
            .createTab()
    }

    fun runCommand(tab: Any, command: String): BridgeResult {
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
                        OpenCodeBridgeService.notify(project, "Cannot focus the OpenCode terminal input component.", NotificationType.WARNING)
                        return@Runnable
                    }
                    IdeFocusManager.getInstance(project)
                        .requestFocusInProject(focusComponent, project)
                        .doWhenDone(Runnable {
            try {
                sendText(view, command)
                scheduleEnter(view, focusComponent, false, "Started OpenCode Terminal (frontend-api)")
                            } catch (exception: Throwable) {
                                OpenCodeBridgeService.notify(project, "Failed to start OpenCode: ${exception.message}", NotificationType.WARNING)
                            }
                        })
                        .doWhenRejected(Runnable {
                            OpenCodeBridgeService.notify(project, "Cannot focus the OpenCode terminal input component.", NotificationType.WARNING)
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

    fun inject(tab: Any, settleAtLineEnd: Boolean, triggerText: String, isCommand: Boolean): BridgeResult {
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
                        OpenCodeBridgeService.notify(project, "Cannot focus the OpenCode terminal input component.", NotificationType.WARNING)
                        return@Runnable
                    }
                    val focusCallback = IdeFocusManager.getInstance(project)
                        .requestFocusInProject(focusComponent, project)
                    focusCallback.doWhenDone(Runnable {
                        try {
                            sendEditorCommand(view, focusComponent, settleAtLineEnd, triggerText, isCommand)
                        } catch (exception: Throwable) {
                            OpenCodeBridgeService.notify(project, "Failed to send editor_open to OpenCode: ${exception.message}", NotificationType.WARNING)
                        }
                    })
                    focusCallback.doWhenRejected(Runnable {
                        OpenCodeBridgeService.notify(project, "Cannot focus the OpenCode terminal input component.", NotificationType.WARNING)
                    })
                })
        }, true, true)

        return BridgeResult.Scheduled
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
                        OpenCodeBridgeService.notify(project, "Cannot focus the OpenCode terminal input component.", NotificationType.WARNING)
                        return@Runnable
                    }
                    val focusCallback = IdeFocusManager.getInstance(project)
                        .requestFocusInProject(focusComponent, project)
                    focusCallback.doWhenDone(Runnable {
                        try {
                            sendDirectInput(view, focusComponent, payload, settleAtLineEnd)
                        } catch (exception: Throwable) {
                            OpenCodeBridgeService.notify(project, "发送 OpenCode 输入失败：${exception.message}", NotificationType.WARNING)
                        }
                    })
                    focusCallback.doWhenRejected(Runnable {
                        OpenCodeBridgeService.notify(project, "Cannot focus the OpenCode terminal input component.", NotificationType.WARNING)
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
            OpenCodeBridgeService.notify(project, "已发送到 OpenCode", NotificationType.INFORMATION)
        }
    }

    private fun sendEditorCommand(
        view: Any,
        focusComponent: JComponent,
        settleAtLineEnd: Boolean,
        triggerText: String,
        isCommand: Boolean
    ) {
        try {
            sendRawString(view, triggerText)
        } catch (_: Throwable) {
            if (isCommand) {
                sendText(view, triggerText.trimEnd('\r'))
            } else {
                throw RuntimeException("Failed to send shortcut sequence.")
            }
        }
        if (isCommand) {
                scheduleEnter(view, focusComponent, settleAtLineEnd)
        } else if (settleAtLineEnd) {
            scheduleLineEndSpace(view, focusComponent, true)
        } else {
            OpenCodeBridgeService.notify(project, "Sent to OpenCode", NotificationType.INFORMATION)
        }
    }

    private fun scheduleEnter(
        view: Any,
        focusComponent: JComponent,
        settleAtLineEnd: Boolean,
        successMessage: String = "Sent to OpenCode"
    ) {
        val timer = Timer(100) {
            try {
                IdeFocusManager.getInstance(project)
                    .requestFocusInProject(focusComponent, project)
                    .doWhenDone(Runnable {
                        pressEnter(view, focusComponent)
                        if (settleAtLineEnd) {
                            scheduleLineEndSpace(view, focusComponent, true)
                        } else {
                            OpenCodeBridgeService.notify(project, successMessage, NotificationType.INFORMATION)
                        }
                    })
                    .doWhenRejected(Runnable {
                        OpenCodeBridgeService.notify(project, "Cannot refocus the OpenCode terminal after editor_open.", NotificationType.WARNING)
                    })
            } catch (exception: Throwable) {
                OpenCodeBridgeService.notify(project, "Failed to invoke Enter: ${exception.message}", NotificationType.WARNING)
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
                            OpenCodeBridgeService.notify(project, "Sent to OpenCode", NotificationType.INFORMATION)
                        }
                    })
                    .doWhenRejected(Runnable {
                        OpenCodeBridgeService.notify(project, "Cannot focus the OpenCode terminal input component.", NotificationType.WARNING)
                    })
            } catch (exception: Throwable) {
                OpenCodeBridgeService.notify(project, "Failed to send trailing space to OpenCode: ${exception.message}", NotificationType.WARNING)
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
