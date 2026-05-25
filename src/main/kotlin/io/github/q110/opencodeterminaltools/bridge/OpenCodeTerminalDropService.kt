// 终端拖拽接收服务 — 将拖到任意终端标签的文件路径发送到已标记 OpenCode 终端
package io.github.q110.opencodeterminaltools.bridge

import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

@Service(Service.Level.PROJECT)
class OpenCodeTerminalDropService(
    private val project: Project
) : Disposable {
    private val contentManagerListeners = mutableMapOf<ContentManager, ContentManagerListener>()
    private val dropTargetDisposables = mutableMapOf<Content, Disposable>()
    private var initialized = false

    fun initialize() {
        if (initialized) {
            return
        }
        initialized = true

        project.messageBus.connect(this)
            .subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
                override fun toolWindowShown(toolWindow: ToolWindow) {
                    if (toolWindow.id == TERMINAL_TOOL_WINDOW_ID) {
                        installForToolWindow(toolWindow)
                    }
                }

                override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
                    if (TERMINAL_TOOL_WINDOW_ID in ids) {
                        installForCurrentTerminalToolWindow()
                    }
                }
            })

        ApplicationManager.getApplication().invokeLater {
            installForCurrentTerminalToolWindow()
        }
    }

    fun refreshDropTarget() {
        val targetContent = currentMarkedContent()
        dropTargetDisposables.keys
            .filter { it !== targetContent }
            .toList()
            .forEach { disposeDropTarget(it) }

        if (targetContent != null) {
            installForContent(targetContent)
        }
    }

    private fun installForCurrentTerminalToolWindow() {
        val toolWindow = TerminalToolWindowManager.getInstance(project).toolWindow
            ?: ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW_ID)
            ?: return
        installForToolWindow(toolWindow)
    }

    private fun installForToolWindow(toolWindow: ToolWindow) {
        val contentManager = toolWindow.contentManager
        if (!contentManagerListeners.containsKey(contentManager)) {
            val listener = object : ContentManagerListener {
                override fun contentAdded(event: ContentManagerEvent) {
                    refreshDropTarget()
                }

                override fun contentRemoved(event: ContentManagerEvent) {
                    disposeDropTarget(event.content)
                    refreshDropTarget()
                }

                override fun selectionChanged(event: ContentManagerEvent) {
                    refreshDropTarget()
                }
            }
            contentManager.addContentManagerListener(listener)
            contentManagerListeners[contentManager] = listener
        }
        refreshDropTarget()
    }

    private fun currentMarkedContent(): Content? {
        val toolWindow = TerminalToolWindowManager.getInstance(project).toolWindow
            ?: ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW_ID)
            ?: return null
        val selectedContent = toolWindow.contentManager.selectedContent ?: return null
        return if (project.service<OpenCodeBridgeService>().isActiveMarkedTerminalContent(selectedContent)) {
            selectedContent
        } else {
            null
        }
    }

    private fun installForContent(content: Content) {
        val component = content.component ?: return
        if (dropTargetDisposables.containsKey(content)) {
            return
        }

        val disposable = Disposer.newDisposable("OpenCode terminal drop target")
        DnDSupport.createBuilder(component)
            .disableAsSource()
            .enableAsNativeTarget()
            .setTargetChecker { event ->
                val files = draggedFiles(event)
                val canDrop = files.isNotEmpty() &&
                    project.service<OpenCodeBridgeService>().isActiveMarkedTerminalContent(content)
                if (canDrop) {
                    event.setDropPossible(true, "发送路径到 OpenCode")
                }
                canDrop
            }
            .setDropHandler { event ->
                val files = draggedFiles(event)
                if (files.isEmpty() || !project.service<OpenCodeBridgeService>().isActiveMarkedTerminalContent(content)) {
                    return@setDropHandler
                }
                val result = project.service<OpenCodeBridgeService>().sendDroppedPaths(files)
                if (result is OpenCodeBridgeService.BridgeResult.Error) {
                    OpenCodeBridgeService.notify(project, result.message, NotificationType.WARNING)
                }
            }
            .setDisposableParent(disposable)
            .install()

        dropTargetDisposables[content] = disposable
    }

    private fun disposeDropTarget(content: Content) {
        val disposable = dropTargetDisposables.remove(content) ?: return
        Disposer.dispose(disposable)
    }

    private fun draggedFiles(event: DnDEvent): List<VirtualFile> {
        val attachedFiles = try {
            FileCopyPasteUtil.getVirtualFileListFromAttachedObject(event.attachedObject)
        } catch (_: Throwable) {
            emptyList()
        }
        if (attachedFiles.isNotEmpty()) {
            return attachedFiles.filter { it.isValid }
        }

        val localFiles = try {
            FileCopyPasteUtil.getFileList(event)
        } catch (_: Throwable) {
            emptyList()
        }
        return localFiles.orEmpty()
            .mapNotNull { LocalFileSystem.getInstance().findFileByIoFile(it) }
            .filter { it.isValid }
    }

    override fun dispose() {
        dropTargetDisposables.keys.toList().forEach { disposeDropTarget(it) }
        contentManagerListeners.forEach { (contentManager, listener) ->
            contentManager.removeContentManagerListener(listener)
        }
        contentManagerListeners.clear()
    }

    companion object {
        private const val TERMINAL_TOOL_WINDOW_ID = "Terminal"
    }
}
