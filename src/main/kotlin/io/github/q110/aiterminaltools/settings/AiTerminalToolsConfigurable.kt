package io.github.q110.aiterminaltools.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
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
    private var dragToAiTerminalCheckBox: JBCheckBox? = null
    private var commitMessageModelField: JBTextField? = null
    private var additionalFileExtensionsField: JBTextField? = null
    private var panel: JPanel? = null

    override fun getDisplayName(): String {
        return "AI Terminal Tools"
    }

    override fun createComponent(): JComponent {
        val fileLinksCheckBox = JBCheckBox("启用文件跳转")
        val copyLinksCheckBox = JBCheckBox("启用点击复制")
        val errorToAiTerminalIconsCheckBox = JBCheckBox("启用控制台错误发送图标")
        val dragToAiTerminalCheckBox = JBCheckBox("启用拖拽文件/文件夹到 AI 终端")
        val commitMessageModelField = JBTextField()
        val additionalFileExtensionsField = JBTextField()
        val defaultFileExtensionsArea = JBTextArea(
            AiTerminalToolsSettings.StateData.DEFAULT_FILE_EXTENSIONS.joinToString(", ")
        )
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
        val fileLinksHelpLabel = JBLabel("关闭后，新输出内容不再生成对应链接。")
        fileLinksHelpLabel.foreground = JBColor.namedColor("Label.disabledForeground", JBColor(0x8c8c8c, 0x999999))
        fileLinksHelpLabel.border = JBUI.Borders.emptyLeft(20)
        panel.add(fileLinksHelpLabel, constraints)

        constraints.gridy = 4
        constraints.insets = JBUI.insetsBottom(8)
        panel.add(dragToAiTerminalCheckBox, constraints)

        constraints.gridy = 5
        constraints.insets = JBUI.insetsTop(4)
        val dragHelpLabel = JBLabel("开启后拖拽文件/文件夹到任意终端均发送为 @路径。关闭后仅对插件启动的终端生效。")
        dragHelpLabel.foreground = JBColor.namedColor("Label.disabledForeground", JBColor(0x8c8c8c, 0x999999))
        dragHelpLabel.border = JBUI.Borders.emptyLeft(20)
        panel.add(dragHelpLabel, constraints)

        constraints.gridy = 6
        constraints.insets = JBUI.insetsTop(16)
        panel.add(JLabel("额外文件扩展名："), constraints)

        constraints.gridy = 7
        constraints.insets = JBUI.insetsTop(4)
        panel.add(additionalFileExtensionsField, constraints)

        constraints.gridy = 8
        val additionalExtensionsHelpLabel = JBLabel("下面列表已默认支持，额外扩展名只填写未包含的项；逗号、分号、空格或换行分隔。")
        additionalExtensionsHelpLabel.foreground = JBColor.namedColor("Label.disabledForeground", JBColor(0x8c8c8c, 0x999999))
        additionalExtensionsHelpLabel.border = JBUI.Borders.emptyLeft(20)
        panel.add(additionalExtensionsHelpLabel, constraints)

        constraints.gridy = 9
        constraints.insets = JBUI.insetsTop(4)
        defaultFileExtensionsArea.isEditable = false
        defaultFileExtensionsArea.lineWrap = true
        defaultFileExtensionsArea.wrapStyleWord = true
        defaultFileExtensionsArea.isOpaque = false
        defaultFileExtensionsArea.foreground = JBColor.namedColor("Label.disabledForeground", JBColor(0x8c8c8c, 0x999999))
        defaultFileExtensionsArea.border = JBUI.Borders.emptyLeft(20)
        panel.add(defaultFileExtensionsArea, constraints)

        constraints.gridy = 10
        constraints.insets = JBUI.insetsTop(16)
        panel.add(JLabel("提交信息模型："), constraints)

        constraints.gridy = 11
        constraints.insets = JBUI.insetsTop(4)
        panel.add(commitMessageModelField, constraints)

        constraints.gridy = 12
        val commitMessageHelpLabel = JBLabel("格式：provider/model，留空用默认模型。")
        commitMessageHelpLabel.foreground = JBColor.namedColor("Label.disabledForeground", JBColor(0x8c8c8c, 0x999999))
        commitMessageHelpLabel.border = JBUI.Borders.emptyLeft(20)
        panel.add(commitMessageHelpLabel, constraints)

        constraints.gridy = 13
        constraints.weighty = 1.0
        constraints.fill = GridBagConstraints.BOTH
        panel.add(JPanel(), constraints)

        this.fileLinksCheckBox = fileLinksCheckBox
        this.copyLinksCheckBox = copyLinksCheckBox
        this.errorToAiTerminalIconsCheckBox = errorToAiTerminalIconsCheckBox
        this.dragToAiTerminalCheckBox = dragToAiTerminalCheckBox
        this.commitMessageModelField = commitMessageModelField
        this.additionalFileExtensionsField = additionalFileExtensionsField
        this.panel = panel
        return panel
    }

    override fun isModified(): Boolean {
        val settings = AiTerminalToolsSettings.getInstance().getState()
        return fileLinksCheckBox?.isSelected != settings.fileLinksEnabled ||
            copyLinksCheckBox?.isSelected != settings.copyLinksEnabled ||
            errorToAiTerminalIconsCheckBox?.isSelected != settings.errorToAiTerminalIconsEnabled ||
            dragToAiTerminalCheckBox?.isSelected != settings.isDragToAiTerminalEnabled() ||
            additionalFileExtensionsField?.text?.trim() != settings.additionalFileExtensions ||
            commitMessageModelField?.text?.trim() != settings.commitMessageModel
    }

    override fun apply() {
        val settings = AiTerminalToolsSettings.getInstance().getState()
        settings.fileLinksEnabled = fileLinksCheckBox?.isSelected == true
        settings.copyLinksEnabled = copyLinksCheckBox?.isSelected == true
        settings.errorToAiTerminalIconsEnabled = errorToAiTerminalIconsCheckBox?.isSelected == true
        settings.dragToAiTerminalEnabled = dragToAiTerminalCheckBox?.isSelected == true
        settings.additionalFileExtensions = additionalFileExtensionsField?.text?.trim().orEmpty()
        settings.commitMessageModel = commitMessageModelField?.text?.trim().orEmpty()
    }

    override fun reset() {
        val settings = AiTerminalToolsSettings.getInstance().getState()
        fileLinksCheckBox?.isSelected = settings.fileLinksEnabled
        copyLinksCheckBox?.isSelected = settings.copyLinksEnabled
        errorToAiTerminalIconsCheckBox?.isSelected = settings.errorToAiTerminalIconsEnabled
        dragToAiTerminalCheckBox?.isSelected = settings.isDragToAiTerminalEnabled()
        additionalFileExtensionsField?.text = settings.additionalFileExtensions
        commitMessageModelField?.text = settings.commitMessageModel
    }

    override fun disposeUIResources() {
        fileLinksCheckBox = null
        copyLinksCheckBox = null
        errorToAiTerminalIconsCheckBox = null
        dragToAiTerminalCheckBox = null
        commitMessageModelField = null
        additionalFileExtensionsField = null
        panel = null
    }
}
