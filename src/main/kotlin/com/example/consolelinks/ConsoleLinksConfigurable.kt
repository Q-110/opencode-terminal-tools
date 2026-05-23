package com.example.consolelinks

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

// Settings -> Tools -> Console Links 中显示的配置页。
class ConsoleLinksConfigurable : Configurable {
    private var fileLinksCheckBox: JBCheckBox? = null
    private var copyLinksCheckBox: JBCheckBox? = null
    private var openCodeEditorOpenShortcutField: JBTextField? = null
    private var panel: JPanel? = null

    override fun getDisplayName(): String {
        return "Console Links"
    }

    override fun createComponent(): JComponent {
        val fileLinksCheckBox = JBCheckBox("启用文件跳转链接")
        val copyLinksCheckBox = JBCheckBox("启用点击复制链接")
        val openCodeEditorOpenShortcutField = JBTextField()
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
        constraints.insets = JBUI.insetsTop(4)
        panel.add(JLabel("关闭后，新输出的控制台文本将不再生成对应类型的链接。"), constraints)

        constraints.gridy = 3
        constraints.insets = JBUI.insetsTop(16)
        panel.add(JLabel("OpenCode 桥接 EDITOR 配置："), constraints)

        constraints.gridy = 4
        constraints.insets = JBUI.insetsTop(4)
        panel.add(JLabel("PowerShell: \$env:EDITOR=\"\$env:TEMP\\opencode-idea-bridge\\opencode-editor.cmd\""), constraints)

        constraints.gridy = 5
        panel.add(JLabel("cmd: set EDITOR=%TEMP%\\opencode-idea-bridge\\opencode-editor.cmd"), constraints)

        constraints.gridy = 6
        constraints.insets = JBUI.insetsTop(4)
        panel.add(JLabel("可选真实编辑器: \$env:OPENCODE_IDEA_REAL_EDITOR=\"code --wait\""), constraints)

        constraints.gridy = 7
        constraints.insets = JBUI.insetsTop(16)
        panel.add(JLabel("OpenCode editor_open 快捷键："), constraints)

        constraints.gridy = 8
        constraints.insets = JBUI.insetsTop(4)
        panel.add(openCodeEditorOpenShortcutField, constraints)

        constraints.gridy = 9
        panel.add(JLabel("默认 OpenCode 配置填写 ctrl+x e；如果你在 tui.json 中改成 F4，则填写 f4。也可以填写 /editor 使用旧流程。"), constraints)

        constraints.gridy = 10
        constraints.weighty = 1.0
        constraints.fill = GridBagConstraints.BOTH
        panel.add(JPanel(), constraints)

        this.fileLinksCheckBox = fileLinksCheckBox
        this.copyLinksCheckBox = copyLinksCheckBox
        this.openCodeEditorOpenShortcutField = openCodeEditorOpenShortcutField
        this.panel = panel
        return panel
    }

    override fun isModified(): Boolean {
        val settings = ConsoleLinksSettings.getInstance().getState()
        return fileLinksCheckBox?.isSelected != settings.fileLinksEnabled ||
            copyLinksCheckBox?.isSelected != settings.copyLinksEnabled ||
            openCodeEditorOpenShortcutField?.text?.trim() != settings.openCodeEditorOpenShortcut
    }

    override fun apply() {
        val settings = ConsoleLinksSettings.getInstance().getState()
        settings.fileLinksEnabled = fileLinksCheckBox?.isSelected == true
        settings.copyLinksEnabled = copyLinksCheckBox?.isSelected == true
        settings.openCodeEditorOpenShortcut = openCodeEditorOpenShortcutField?.text?.trim()?.ifEmpty { "ctrl+x e" } ?: "ctrl+x e"
    }

    override fun reset() {
        val settings = ConsoleLinksSettings.getInstance().getState()
        fileLinksCheckBox?.isSelected = settings.fileLinksEnabled
        copyLinksCheckBox?.isSelected = settings.copyLinksEnabled
        openCodeEditorOpenShortcutField?.text = settings.openCodeEditorOpenShortcut
    }

    override fun disposeUIResources() {
        fileLinksCheckBox = null
        copyLinksCheckBox = null
        openCodeEditorOpenShortcutField = null
        panel = null
    }
}
