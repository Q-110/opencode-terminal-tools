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

internal class FileChoiceDialog(
    private val project: Project,
    private val files: List<VirtualFile>
) : DialogWrapper(project) {
    private val list = JBList(files.map { displayPath(project, it) })

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

    private fun dialogSize(project: Project): Dimension {
        val windowSize = WindowManager.getInstance().getFrame(project)?.size
            ?: Toolkit.getDefaultToolkit().screenSize
        return Dimension((windowSize.width * 0.5).toInt(), (windowSize.height * 0.5).toInt())
    }
}
