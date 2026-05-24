package io.github.q110.opencodeterminaltools.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class OpenCodeTerminalToolsConfigurable : Configurable {
    private var fileLinksCheckBox: JBCheckBox? = null
    private var copyLinksCheckBox: JBCheckBox? = null
    private var openCodeEditorOpenShortcutField: JBTextField? = null
    private var panel: JPanel? = null

    override fun getDisplayName(): String {
        return "OpenCode Terminal Tools"
    }

    override fun createComponent(): JComponent {
        val fileLinksCheckBox = JBCheckBox("Enable file navigation links")
        val copyLinksCheckBox = JBCheckBox("Enable click-to-copy links")
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
        panel.add(JLabel("When disabled, newly printed terminal text will not receive matching links."), constraints)

        constraints.gridy = 3
        constraints.insets = JBUI.insetsTop(16)
        panel.add(JLabel("OpenCode bridge EDITOR setup:"), constraints)

        constraints.gridy = 4
        constraints.insets = JBUI.insetsTop(4)
        panel.add(JLabel("Start OpenCode Terminal sets EDITOR automatically before running opencode."), constraints)

        constraints.gridy = 5
        panel.add(JLabel("IDEA 2025.1 defaults to Classic Terminal; Reworked 2025 is Beta and must be selected in IDE settings."), constraints)

        constraints.gridy = 6
        panel.add(JLabel("Reworked startup is reliable on 2025.3+; 2025.1/2025.2 use best-effort internal APIs and may fall back to Classic."), constraints)

        constraints.gridy = 7
        constraints.insets = JBUI.insetsTop(8)
        panel.add(JLabel("PowerShell: \$env:EDITOR=\"\$env:TEMP\\opencode-idea-bridge\\opencode-editor.cmd\""), constraints)

        constraints.gridy = 8
        constraints.insets = JBUI.insetsTop(4)
        panel.add(JLabel("cmd: set EDITOR=%TEMP%\\opencode-idea-bridge\\opencode-editor.cmd"), constraints)

        constraints.gridy = 9
        panel.add(JLabel("Optional real editor: \$env:OPENCODE_IDEA_REAL_EDITOR=\"code --wait\""), constraints)

        constraints.gridy = 10
        constraints.insets = JBUI.insetsTop(16)
        panel.add(JLabel("OpenCode editor_open shortcut:"), constraints)

        constraints.gridy = 11
        constraints.insets = JBUI.insetsTop(4)
        panel.add(openCodeEditorOpenShortcutField, constraints)

        constraints.gridy = 12
        panel.add(JLabel("Default is ctrl+x e. If tui.json changes editor_open, enter the matching shortcut here. You can also enter /editor."), constraints)

        constraints.gridy = 13
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
        val settings = OpenCodeTerminalToolsSettings.getInstance().getState()
        return fileLinksCheckBox?.isSelected != settings.fileLinksEnabled ||
            copyLinksCheckBox?.isSelected != settings.copyLinksEnabled ||
            openCodeEditorOpenShortcutField?.text?.trim() != settings.openCodeEditorOpenShortcut
    }

    override fun apply() {
        val settings = OpenCodeTerminalToolsSettings.getInstance().getState()
        settings.fileLinksEnabled = fileLinksCheckBox?.isSelected == true
        settings.copyLinksEnabled = copyLinksCheckBox?.isSelected == true
        settings.openCodeEditorOpenShortcut = openCodeEditorOpenShortcutField?.text?.trim()?.ifEmpty { "ctrl+x e" } ?: "ctrl+x e"
    }

    override fun reset() {
        val settings = OpenCodeTerminalToolsSettings.getInstance().getState()
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
