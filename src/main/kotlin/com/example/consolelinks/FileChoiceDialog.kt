package com.example.consolelinks

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

// 当同名文件无法自动消歧时显示的文件选择对话框。
internal class FileChoiceDialog(
    private val project: Project,
    private val files: List<VirtualFile>
) : DialogWrapper(project) {
    // 列表展示项目内相对路径，帮助用户区分不同目录下的同名文件。
    private val list = JBList(files.map { displayPath(project, it) })

    // 根据列表当前选中项返回对应的真实文件。
    val selectedFile: VirtualFile?
        get() = files.getOrNull(list.selectedIndex)

    init {
        title = "选择文件"
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.selectedIndex = 0
        init()
    }

    override fun createCenterPanel(): JComponent {
        // 对话框主体只包含提示文字和候选文件列表，保持选择流程直接。
        val panel = JPanel(BorderLayout())
        panel.add(JLabel("发现多个同名文件，请选择要跳转的文件。"), BorderLayout.NORTH)
        panel.add(JBScrollPane(list), BorderLayout.CENTER)
        panel.preferredSize = dialogSize(project)
        return panel
    }

    private fun dialogSize(project: Project): Dimension {
        // 按当前 IDE 窗口的一半设置尺寸，避免候选路径较长时显示空间过小。
        val windowSize = WindowManager.getInstance().getFrame(project)?.size
            ?: Toolkit.getDefaultToolkit().screenSize
        return Dimension((windowSize.width * 0.5).toInt(), (windowSize.height * 0.5).toInt())
    }
}
