package io.github.q110.aiterminaltools.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class AiTerminalToolsConfigurable : Configurable {
    private var fileLinksCheckBox: JBCheckBox? = null
    private var copyLinksCheckBox: JBCheckBox? = null
    private var errorToAiTerminalIconsCheckBox: JBCheckBox? = null
    private var commitMessageModelField: JBTextField? = null
    private var panel: JPanel? = null

    override fun getDisplayName(): String {
        return "AI Terminal Tools"
    }

    override fun createComponent(): JComponent {
        val fileLinksCheckBox = JBCheckBox("启用文件跳转")
        val copyLinksCheckBox = JBCheckBox("启用点击复制")
        val errorToAiTerminalIconsCheckBox = JBCheckBox("启用控制台错误发送图标")
        val commitMessageModelField = JBTextField()
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(12)

        val constraints = GridBagConstraints()
        constraints.gridx = 0
        constraints.gridy = 0
        constraints.anchor = GridBagConstraints.WEST
        constraints.fill = GridBagConstraints.HORIZONTAL
        constraints.weightx = 1.0
        constraints.insets = JBUI.insetsBottom(8)
        panel.add(fileLinksCheckBox, constraints)

        constraints.gridy = 1
        panel.add(copyLinksCheckBox, constraints)

        constraints.gridy = 2
        panel.add(errorToAiTerminalIconsCheckBox, constraints)

        constraints.gridy = 3
        constraints.insets = JBUI.insetsTop(4)
        panel.add(JLabel("关闭后，新输出内容不再生成对应链接。"), constraints)

        constraints.gridy = 4
        constraints.insets = JBUI.insetsTop(16)
        panel.add(JLabel("AI 终端桥接："), constraints)

        constraints.gridy = 5
        constraints.insets = JBUI.insetsTop(4)
        panel.add(JLabel("启动按钮会打开终端并运行对应工具。"), constraints)

        constraints.gridy = 6
        constraints.insets = JBUI.insetsTop(16)
        panel.add(JLabel("提交信息模型："), constraints)

        constraints.gridy = 7
        constraints.insets = JBUI.insetsTop(4)
        panel.add(commitMessageModelField, constraints)

        constraints.gridy = 8
        panel.add(JLabel("格式：provider/model，留空用默认模型。"), constraints)

        constraints.gridy = 9
        constraints.weighty = 1.0
        constraints.fill = GridBagConstraints.BOTH
        panel.add(JPanel(), constraints)

        this.fileLinksCheckBox = fileLinksCheckBox
        this.copyLinksCheckBox = copyLinksCheckBox
        this.errorToAiTerminalIconsCheckBox = errorToAiTerminalIconsCheckBox
        this.commitMessageModelField = commitMessageModelField
        this.panel = panel
        return panel
    }

    override fun isModified(): Boolean {
        val settings = AiTerminalToolsSettings.getInstance().getState()
        return fileLinksCheckBox?.isSelected != settings.fileLinksEnabled ||
            copyLinksCheckBox?.isSelected != settings.copyLinksEnabled ||
            errorToAiTerminalIconsCheckBox?.isSelected != settings.errorToAiTerminalIconsEnabled ||
            commitMessageModelField?.text?.trim() != settings.commitMessageModel
    }

    override fun apply() {
        val settings = AiTerminalToolsSettings.getInstance().getState()
        settings.fileLinksEnabled = fileLinksCheckBox?.isSelected == true
        settings.copyLinksEnabled = copyLinksCheckBox?.isSelected == true
        settings.errorToAiTerminalIconsEnabled = errorToAiTerminalIconsCheckBox?.isSelected == true
        settings.commitMessageModel = commitMessageModelField?.text?.trim().orEmpty()
    }

    override fun reset() {
        val settings = AiTerminalToolsSettings.getInstance().getState()
        fileLinksCheckBox?.isSelected = settings.fileLinksEnabled
        copyLinksCheckBox?.isSelected = settings.copyLinksEnabled
        errorToAiTerminalIconsCheckBox?.isSelected = settings.errorToAiTerminalIconsEnabled
        commitMessageModelField?.text = settings.commitMessageModel
    }

    override fun disposeUIResources() {
        fileLinksCheckBox = null
        copyLinksCheckBox = null
        errorToAiTerminalIconsCheckBox = null
        commitMessageModelField = null
        panel = null
    }
}
