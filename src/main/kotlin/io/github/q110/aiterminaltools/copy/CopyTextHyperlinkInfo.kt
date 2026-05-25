// 点击复制链接处理器 — 点击后将匹配文本复制到剪贴板并显示 "已复制" 提示
package io.github.q110.aiterminaltools.copy

import com.intellij.execution.filters.HyperlinkInfoBase
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Insets
import java.awt.Point
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class CopyTextHyperlinkInfo(
    private val project: Project,
    private val text: String
) : HyperlinkInfoBase() {
    /** 复制文本并在链接上方显示持续 700ms 的 Balloon 提示 */
    override fun navigate(project: Project, hyperlinkLocationPoint: RelativePoint?) {
        CopyPasteManager.copyTextToClipboard(text)
        if (hyperlinkLocationPoint == null) {
            return
        }

        val content = JPanel(BorderLayout())
        content.preferredSize = Dimension(64, 28)
        content.minimumSize = Dimension(64, 28)
        content.maximumSize = Dimension(64, 28)
        content.border = JBUI.Borders.empty()
        content.add(JBLabel("已复制", SwingConstants.CENTER), BorderLayout.CENTER)

        val balloon = JBPopupFactory.getInstance()
            .createBalloonBuilder(content)
            .setSmallVariant(true)
            .setShowCallout(false)
            .setCloseButtonEnabled(false)
            .setContentInsets(Insets(0, 0, 0, 0))
            .setFadeoutTime(700)
            .createBalloon()
        val component = hyperlinkLocationPoint.component

        val point = if (component.width > 0) {
            RelativePoint(component, Point(component.width / 2, 18))
        } else {
            hyperlinkLocationPoint
        }

        balloon.show(point, Balloon.Position.below)
    }
}
