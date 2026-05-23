// 封装新版终端（Reworked Terminal）的所有 API 调用。仅在 2025.3+ IDE 中可加载。
package io.github.q110.opencodeterminaltools.bridge

import com.intellij.notification.NotificationType
import io.github.q110.opencodeterminaltools.bridge.OpenCodeBridgeService.BridgeResult
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.TerminalView
import java.awt.Component
import java.awt.Robot
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.Timer

class FrontendTerminalHelper(private val project: Project) {

    /** 获取当前选中的前端终端标签页 */
    fun selectedTerminal(): Any? {
        val selectedContent = ToolWindowManager.getInstance(project)
            .getToolWindow(TERMINAL_TOOL_WINDOW_ID)
            ?.contentManager?.selectedContent ?: return null
        return TerminalToolWindowTabsManager.getInstance(project)
            .tabs
            .firstOrNull { it.content == selectedContent }
    }

    /** 获取所有前端终端标签页 */
    fun allTerminals(): List<Any> = TerminalToolWindowTabsManager.getInstance(project).tabs.toList()

    /** 检查标签页是否仍存在 */
    fun isTabExists(tab: Any): Boolean {
        return TerminalToolWindowTabsManager.getInstance(project)
            .tabs
            .any { it == tab }
    }

    /** 注入 editor_open 到前端终端 */
    fun inject(tab: Any, settleAtLineEnd: Boolean, triggerText: String, isCommand: Boolean): BridgeResult {
        val frontendTab = tab as? TerminalToolWindowTab
            ?: return BridgeResult.Error("无效的前端终端标签页")

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW_ID)
            ?: return BridgeResult.Error("没有找到 Terminal 工具窗口。")

        toolWindow.activate(Runnable {
            val view = frontendTab.view
            toolWindow.contentManager.setSelectedContentCB(frontendTab.content, true, true)
                .doWhenProcessed(Runnable {
                    val focusComponent = view.preferredFocusableComponent
                    val focusCallback = IdeFocusManager.getInstance(project)
                        .requestFocusInProject(focusComponent, project)
                    focusCallback.doWhenDone(Runnable {
                        try {
                            sendEditorCommand(view, focusComponent, settleAtLineEnd, triggerText, isCommand)
                        } catch (exception: Throwable) {
                            OpenCodeBridgeService.notify(project, "发送 OpenCode editor_open 失败：${exception.message}", NotificationType.WARNING)
                        }
                    })
                    focusCallback.doWhenRejected(Runnable {
                        OpenCodeBridgeService.notify(project, "无法聚焦 OpenCode Terminal 输入组件，未发送 editor_open。", NotificationType.WARNING)
                    })
                })
        }, true, true)

        return BridgeResult.Scheduled
    }

    /** 聚焦后发送快捷键序列 + 处理行尾空格/回车 */
    private fun sendEditorCommand(view: TerminalView, focusComponent: JComponent, settleAtLineEnd: Boolean, triggerText: String, isCommand: Boolean) {
        try {
            sendRawString(view, triggerText)
        } catch (_: Throwable) {
            if (isCommand) {
                view.sendText(triggerText.trimEnd('\r'))
            } else {
                throw RuntimeException("发送快捷键序列失败")
            }
        }
        if (isCommand) {
            scheduleEnter(view, focusComponent, settleAtLineEnd)
        } else if (settleAtLineEnd) {
            scheduleLineEndSpace(view, focusComponent, true)
        } else {
            OpenCodeBridgeService.notify(project, "已发送到 OpenCode", NotificationType.INFORMATION)
        }
    }

    /** 延时后发送 Enter */
    private fun scheduleEnter(view: TerminalView, focusComponent: JComponent, settleAtLineEnd: Boolean) {
        val timer = Timer(100) {
            try {
                IdeFocusManager.getInstance(project)
                    .requestFocusInProject(focusComponent, project)
                    .doWhenDone(Runnable {
                        pressEnter(view, focusComponent)
                        if (settleAtLineEnd) {
                            scheduleLineEndSpace(view, focusComponent, true)
                        } else {
                            OpenCodeBridgeService.notify(project, "已发送到 OpenCode", NotificationType.INFORMATION)
                        }
                    })
                    .doWhenRejected(Runnable {
                        OpenCodeBridgeService.notify(project, "无法在发送 editor_open 后重新聚焦 OpenCode Terminal，未调用 Enter。", NotificationType.WARNING)
                    })
            } catch (exception: Throwable) {
                OpenCodeBridgeService.notify(project, "调用 Enter 失败：${exception.message}", NotificationType.WARNING)
            }
        }
        timer.isRepeats = false
        timer.start()
    }

    /** 延时后发送行尾空格 */
    private fun scheduleLineEndSpace(view: TerminalView, focusComponent: JComponent, notifyAfter: Boolean) {
        val timer = Timer(300) {
            try {
                IdeFocusManager.getInstance(project)
                    .requestFocusInProject(focusComponent, project)
                    .doWhenDone(Runnable {
                        sendLineEndSpace(view)
                        if (notifyAfter) {
                            OpenCodeBridgeService.notify(project, "已发送到 OpenCode", NotificationType.INFORMATION)
                        }
                    })
                    .doWhenRejected(Runnable {
                        OpenCodeBridgeService.notify(project, "无法聚焦 OpenCode Terminal 输入组件，未发送行尾空格。", NotificationType.WARNING)
                    })
            } catch (exception: Throwable) {
                OpenCodeBridgeService.notify(project, "发送 OpenCode 行尾空格失败：${exception.message}", NotificationType.WARNING)
            }
        }
        timer.isRepeats = false
        timer.start()
    }

    /** 三层次 Enter Fallback */
    private fun pressEnter(view: TerminalView, focusComponent: JComponent) {
        try {
            Robot().keyPress(KeyEvent.VK_ENTER).also { Robot().keyRelease(KeyEvent.VK_ENTER) }
            return
        } catch (_: Throwable) { }
        try {
            callSendEnter(view)
            return
        } catch (_: Throwable) { }
        dispatchEnterKeyEvent(focusComponent)
    }

    /** 反射调用 terminalInput.sendString() */
    private fun sendRawString(view: TerminalView, text: String) {
        val input = terminalInput(view)
        input.javaClass.getMethod("sendString", String::class.java).invoke(input, text)
    }

    /** 发送行尾空格：优先反射 → 退化 view.sendText */
    private fun sendLineEndSpace(view: TerminalView) {
        try {
            sendRawString(view, LINE_END_SPACE)
        } catch (_: Throwable) {
            view.sendText(LINE_END_SPACE)
        }
    }

    /** 反射调用 terminalInput.sendEnter() */
    private fun callSendEnter(view: TerminalView) {
        val input = terminalInput(view)
        input.javaClass.getMethod("sendEnter").invoke(input)
    }

    /** 从 TerminalView 反射获取 internal 字段 terminalInput */
    private fun terminalInput(view: TerminalView): Any {
        return findField(view.javaClass, "terminalInput")
            ?.let { field -> field.also { it.isAccessible = true }.get(view) }
            ?: throw IllegalStateException("无法访问新版 Terminal 输入通道")
    }

    /** 直接向组件派发 KeyEvent */
    private fun dispatchEnterKeyEvent(component: Component) {
        val now = System.currentTimeMillis()
        component.dispatchEvent(KeyEvent(component, KeyEvent.KEY_PRESSED, now, 0, KeyEvent.VK_ENTER, '\n'))
        component.dispatchEvent(KeyEvent(component, KeyEvent.KEY_RELEASED, now, 0, KeyEvent.VK_ENTER, '\n'))
    }

    /** 沿类继承链向上查找字段 */
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
