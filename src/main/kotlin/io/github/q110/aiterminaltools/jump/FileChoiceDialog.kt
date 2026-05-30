// 同名文件选择弹窗 — 当多个文件匹配时让用户手动选择
package io.github.q110.aiterminaltools.jump

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import io.github.q110.aiterminaltools.filter.displayPath
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

internal class FileChoiceDialog(
    private val project: Project,
    private val files: List<VirtualFile>
) : DialogWrapper(project) {
    // 列表里显示相对/友好的路径，方便用户区分同名文件。
    private val list = JBList(files.map { displayPath(project, it) })

    // 当前选中的条目对应的原始文件；如果没有有效选择则返回 null。
    val selectedFile: VirtualFile?
        get() = files.getOrNull(list.selectedIndex)

    init {
        title = "选择文件"
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.selectedIndex = 0
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(JLabel("发现多个同名文件，请选择要跳转的文件。"), BorderLayout.NORTH)
        panel.add(JBScrollPane(list), BorderLayout.CENTER)
        panel.preferredSize = dialogSize(project)
        return panel
    }

    // 弹窗按当前窗口尺寸优先，其次退回到屏幕尺寸，避免过大或过小。
    private fun dialogSize(project: Project): Dimension {
        val windowSize = WindowManager.getInstance().getFrame(project)?.size
            ?: Toolkit.getDefaultToolkit().screenSize
        return Dimension((windowSize.width * 0.5).toInt(), (windowSize.height * 0.5).toInt())
    }
}
