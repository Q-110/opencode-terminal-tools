// 独立 JFrame 窗口展示多文件 Diff，通过 DwmSetWindowAttribute 适配 Windows 暗色标题栏
package io.github.q110.aiterminaltools.monitor

import com.intellij.diff.DiffManager
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.awt.BorderLayout
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JPanel

/**
 * 在独立 [JFrame] 窗口中展示多文件 Diff，带文件切换下拉框。
 *
 * [DialogWrapper] 底层 [javax.swing.JDialog] 在 Windows 原生层缺少
 * `WS_MINIMIZEBOX | WS_MAXIMIZEBOX` 样式位，不提供最大化/最小化按钮。
 * 改用 [JFrame]（原生支持完整窗口按钮），并通过 Windows 10+ 的
 * `DwmSetWindowAttribute(DWMWA_USE_IMMERSIVE_DARK_MODE)` 将标题栏
 * 改为暗色以适配 IntelliJ 深色主题。
 */
class AiTurnDiffDialog(
    project: Project,
    private val requests: List<DiffRequest>
) {
    private val disposable = Disposer.newDisposable().also {
        Disposer.register(project, it)
    }

    private val frame = JFrame().apply {
        title = "AI Terminal 本轮修改 - ${requests.size} 个文件"
        isResizable = true
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        setSize(1000, 700)
        setLocationRelativeTo(null)
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                Disposer.dispose(disposable)
            }
        })
    }

    private val comboBox = JComboBox(requests.map { it.title }.toTypedArray())
    private val diffPanel = DiffManager.getInstance().createRequestPanel(project, disposable, frame)

    private var currentIndex = 0

    init {
        comboBox.addActionListener {
            val idx = comboBox.selectedIndex
            if (idx >= 0 && idx < requests.size && idx != currentIndex) {
                currentIndex = idx
                diffPanel.setRequest(requests[idx])
            }
        }

        val container = JPanel(BorderLayout(0, 4))
        container.add(comboBox, BorderLayout.NORTH)
        container.add(diffPanel.component, BorderLayout.CENTER)
        frame.contentPane.add(container)
    }

    fun show() {
        frame.isVisible = true
        applyDarkTitleBar(frame)
        if (requests.isNotEmpty()) {
            diffPanel.setRequest(requests[0])
        }
    }

    // ---- Windows 暗色标题栏 ----

    private fun applyDarkTitleBar(window: Window) {
        if (!SystemInfo.isWin10OrNewer) return
        try {
            val hwnd = Native.getComponentPointer(window)
            // DWMWA_USE_IMMERSIVE_DARK_MODE = 20, pvAttribute 指向值为 TRUE 的 BOOL
            val useDarkMode = IntByReference(1)
            DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, 20, useDarkMode.pointer, 4)
        } catch (_: Throwable) {
            // 非 Windows 或 JNA 不可用，静默跳过
        }
    }

    /** JNA 映射 dwmapi.dll 的 DwmSetWindowAttribute */
    private interface DwmApi : Library {
        companion object {
            val INSTANCE: DwmApi = Native.load("dwmapi", DwmApi::class.java)
        }

        fun DwmSetWindowAttribute(
            hwnd: Pointer?,
            dwAttribute: Int,
            pvAttribute: Pointer?,
            cbAttribute: Int
        ): Int // HRESULT
    }
}
