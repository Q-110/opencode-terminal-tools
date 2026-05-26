package io.github.q110.aiterminaltools.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
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
    private var commitMessageAiToolCombo: ComboBox<String>? = null
    private var commitMessageModelField: JBTextField? = null
    private var commitMessageAdditionalPromptArea: JBTextArea? = null
    private var additionalFileExtensionsField: JBTextField? = null
    private var panel: JPanel? = null
    private var selectedCommitMessageAiTool: String = COMMIT_MESSAGE_AI_TOOL_OPENCODE
    private var openCodeCommitMessageModel: String = ""
    private var claudeCommitMessageModel: String = ""
    private var updatingCommitMessageUi: Boolean = false

    override fun getDisplayName(): String {
        return "AI Terminal Tools"
    }

    override fun createComponent(): JComponent {
        val fileLinksCheckBox = JBCheckBox("启用文件跳转")
        val copyLinksCheckBox = JBCheckBox("启用点击复制")
        val errorToAiTerminalIconsCheckBox = JBCheckBox("启用控制台错误发送图标")
        val dragToAiTerminalCheckBox = JBCheckBox("启用拖拽文件/文件夹到 AI 终端")
        val commitMessageAiToolCombo = ComboBox(arrayOf(COMMIT_MESSAGE_AI_TOOL_OPENCODE_LABEL, COMMIT_MESSAGE_AI_TOOL_CLAUDE_LABEL))
        val commitMessageModelField = JBTextField()
        val commitMessageAdditionalPromptArea = JBTextArea(4, 48)
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
        constraints.insets = JBUI.insetsBottom(8)
        panel.add(dragToAiTerminalCheckBox, constraints)

        constraints.gridy = 4
        constraints.insets = JBUI.insetsTop(4)
        val dragHelpLabel = JBLabel("开启后拖拽文件/文件夹到任意终端均发送为 @路径。关闭后仅对插件启动的终端生效。")
        dragHelpLabel.foreground = JBColor.namedColor("Label.disabledForeground", JBColor(0x8c8c8c, 0x999999))
        dragHelpLabel.border = JBUI.Borders.emptyLeft(20)
        panel.add(dragHelpLabel, constraints)

        constraints.gridy = 5
        constraints.insets = JBUI.insetsTop(16)
        panel.add(JLabel("提交信息 AI 工具："), constraints)

        constraints.gridy = 6
        constraints.insets = JBUI.insetsTop(4)
        panel.add(commitMessageAiToolCombo, constraints)

        constraints.gridy = 7
        constraints.insets = JBUI.insetsTop(16)
        panel.add(JLabel("提交信息模型："), constraints)

        constraints.gridy = 8
        constraints.insets = JBUI.insetsTop(4)
        panel.add(commitMessageModelField, constraints)

        constraints.gridy = 9
        constraints.insets = JBUI.insetsTop(16)
        panel.add(JLabel("提交信息附加提示词："), constraints)

        constraints.gridy = 10
        constraints.insets = JBUI.insetsTop(4)
        commitMessageAdditionalPromptArea.lineWrap = true
        commitMessageAdditionalPromptArea.wrapStyleWord = true
        panel.add(JBScrollPane(commitMessageAdditionalPromptArea), constraints)

        constraints.gridy = 11
        constraints.insets = JBUI.insetsTop(16)
        panel.add(JLabel("额外文件扩展名："), constraints)

        constraints.gridy = 12
        constraints.insets = JBUI.insetsTop(4)
        panel.add(additionalFileExtensionsField, constraints)

        constraints.gridy = 13
        val additionalExtensionsHelpLabel = JBLabel("下面列表已默认支持，额外扩展名只填写未包含的项；使用英文分号分隔。")
        additionalExtensionsHelpLabel.foreground = JBColor.namedColor("Label.disabledForeground", JBColor(0x8c8c8c, 0x999999))
        additionalExtensionsHelpLabel.border = JBUI.Borders.emptyLeft(20)
        panel.add(additionalExtensionsHelpLabel, constraints)

        constraints.gridy = 14
        constraints.insets = JBUI.insetsTop(4)
        defaultFileExtensionsArea.isEditable = false
        defaultFileExtensionsArea.lineWrap = true
        defaultFileExtensionsArea.wrapStyleWord = true
        defaultFileExtensionsArea.isOpaque = false
        defaultFileExtensionsArea.foreground = JBColor.namedColor("Label.disabledForeground", JBColor(0x8c8c8c, 0x999999))
        defaultFileExtensionsArea.border = JBUI.Borders.emptyLeft(20)
        panel.add(defaultFileExtensionsArea, constraints)

        constraints.gridy = 15
        constraints.weighty = 1.0
        constraints.fill = GridBagConstraints.BOTH
        panel.add(JPanel(), constraints)

        commitMessageAiToolCombo.addActionListener {
            if (updatingCommitMessageUi) return@addActionListener
            saveCurrentCommitMessageModel()
            selectedCommitMessageAiTool = commitMessageAiToolFromLabel(commitMessageAiToolCombo.selectedItem as? String)
            updateCommitMessageModelUi()
        }

        this.fileLinksCheckBox = fileLinksCheckBox
        this.copyLinksCheckBox = copyLinksCheckBox
        this.errorToAiTerminalIconsCheckBox = errorToAiTerminalIconsCheckBox
        this.dragToAiTerminalCheckBox = dragToAiTerminalCheckBox
        this.commitMessageAiToolCombo = commitMessageAiToolCombo
        this.commitMessageModelField = commitMessageModelField
        this.commitMessageAdditionalPromptArea = commitMessageAdditionalPromptArea
        this.additionalFileExtensionsField = additionalFileExtensionsField
        this.panel = panel
        return panel
    }

    override fun isModified(): Boolean {
        val settings = AiTerminalToolsSettings.getInstance().getState()
        val currentAiTool = selectedCommitMessageAiTool
        val settingsAiTool = normalizedCommitMessageAiTool(settings.commitMessageAiTool)
        val currentOpenCodeModel = if (currentAiTool == COMMIT_MESSAGE_AI_TOOL_OPENCODE) {
            commitMessageModelField?.text?.trim().orEmpty()
        } else {
            openCodeCommitMessageModel
        }
        val currentClaudeModel = if (currentAiTool == COMMIT_MESSAGE_AI_TOOL_CLAUDE) {
            commitMessageModelField?.text?.trim().orEmpty()
        } else {
            claudeCommitMessageModel
        }
        return fileLinksCheckBox?.isSelected != settings.fileLinksEnabled ||
            copyLinksCheckBox?.isSelected != settings.copyLinksEnabled ||
            errorToAiTerminalIconsCheckBox?.isSelected != settings.errorToAiTerminalIconsEnabled ||
            dragToAiTerminalCheckBox?.isSelected != settings.isDragToAiTerminalEnabled() ||
            additionalFileExtensionsField?.text?.trim() != settings.additionalFileExtensions ||
            currentAiTool != settingsAiTool ||
            currentOpenCodeModel != settings.commitMessageModel ||
            currentClaudeModel != settings.claudeCommitMessageModel ||
            commitMessageAdditionalPromptArea?.text?.trim() != settings.commitMessageAdditionalPrompt
    }

    override fun apply() {
        saveCurrentCommitMessageModel()
        val settings = AiTerminalToolsSettings.getInstance().getState()
        settings.fileLinksEnabled = fileLinksCheckBox?.isSelected == true
        settings.copyLinksEnabled = copyLinksCheckBox?.isSelected == true
        settings.errorToAiTerminalIconsEnabled = errorToAiTerminalIconsCheckBox?.isSelected == true
        settings.dragToAiTerminalEnabled = dragToAiTerminalCheckBox?.isSelected == true
        settings.additionalFileExtensions = additionalFileExtensionsField?.text?.trim().orEmpty()
        settings.commitMessageAiTool = selectedCommitMessageAiTool
        settings.commitMessageModel = openCodeCommitMessageModel
        settings.claudeCommitMessageModel = claudeCommitMessageModel
        settings.commitMessageAdditionalPrompt = commitMessageAdditionalPromptArea?.text?.trim().orEmpty()
    }

    override fun reset() {
        val settings = AiTerminalToolsSettings.getInstance().getState()
        fileLinksCheckBox?.isSelected = settings.fileLinksEnabled
        copyLinksCheckBox?.isSelected = settings.copyLinksEnabled
        errorToAiTerminalIconsCheckBox?.isSelected = settings.errorToAiTerminalIconsEnabled
        dragToAiTerminalCheckBox?.isSelected = settings.isDragToAiTerminalEnabled()
        additionalFileExtensionsField?.text = settings.additionalFileExtensions
        selectedCommitMessageAiTool = normalizedCommitMessageAiTool(settings.commitMessageAiTool)
        openCodeCommitMessageModel = settings.commitMessageModel
        claudeCommitMessageModel = settings.claudeCommitMessageModel
        commitMessageAdditionalPromptArea?.text = settings.commitMessageAdditionalPrompt
        updatingCommitMessageUi = true
        commitMessageAiToolCombo?.selectedItem = commitMessageAiToolLabel(selectedCommitMessageAiTool)
        updatingCommitMessageUi = false
        updateCommitMessageModelUi()
    }

    override fun disposeUIResources() {
        fileLinksCheckBox = null
        copyLinksCheckBox = null
        errorToAiTerminalIconsCheckBox = null
        dragToAiTerminalCheckBox = null
        commitMessageAiToolCombo = null
        commitMessageModelField = null
        commitMessageAdditionalPromptArea = null
        additionalFileExtensionsField = null
        panel = null
    }

    private fun saveCurrentCommitMessageModel() {
        val model = commitMessageModelField?.text?.trim().orEmpty()
        if (selectedCommitMessageAiTool == COMMIT_MESSAGE_AI_TOOL_CLAUDE) {
            claudeCommitMessageModel = model
        } else {
            openCodeCommitMessageModel = model
        }
    }

    private fun updateCommitMessageModelUi() {
        if (selectedCommitMessageAiTool == COMMIT_MESSAGE_AI_TOOL_CLAUDE) {
            commitMessageModelField?.text = claudeCommitMessageModel
        } else {
            commitMessageModelField?.text = openCodeCommitMessageModel
        }
    }

    private fun commitMessageAiToolFromLabel(label: String?): String {
        return if (label == COMMIT_MESSAGE_AI_TOOL_CLAUDE_LABEL) {
            COMMIT_MESSAGE_AI_TOOL_CLAUDE
        } else {
            COMMIT_MESSAGE_AI_TOOL_OPENCODE
        }
    }

    private fun commitMessageAiToolLabel(aiTool: String): String {
        return if (aiTool == COMMIT_MESSAGE_AI_TOOL_CLAUDE) {
            COMMIT_MESSAGE_AI_TOOL_CLAUDE_LABEL
        } else {
            COMMIT_MESSAGE_AI_TOOL_OPENCODE_LABEL
        }
    }

    private fun normalizedCommitMessageAiTool(aiTool: String): String {
        return if (aiTool == COMMIT_MESSAGE_AI_TOOL_CLAUDE) {
            COMMIT_MESSAGE_AI_TOOL_CLAUDE
        } else {
            COMMIT_MESSAGE_AI_TOOL_OPENCODE
        }
    }

    companion object {
        private const val COMMIT_MESSAGE_AI_TOOL_OPENCODE = "opencode"
        private const val COMMIT_MESSAGE_AI_TOOL_CLAUDE = "claude"
        private const val COMMIT_MESSAGE_AI_TOOL_OPENCODE_LABEL = "OpenCode"
        private const val COMMIT_MESSAGE_AI_TOOL_CLAUDE_LABEL = "Claude Code"
    }
}
