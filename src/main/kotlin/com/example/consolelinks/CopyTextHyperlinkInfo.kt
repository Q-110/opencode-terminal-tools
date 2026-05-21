package com.example.consolelinks

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

// 可复制文本链接的点击处理器，点击后把命中文本放入系统剪贴板。
internal class CopyTextHyperlinkInfo(
    private val project: Project,
    private val text: String
) : HyperlinkInfoBase() {
    override fun navigate(project: Project, hyperlinkLocationPoint: RelativePoint?) {
        // 复制动作本身不依赖鼠标位置，因此无论是否能显示提示都先执行复制。
        CopyPasteManager.copyTextToClipboard(text)
        if (hyperlinkLocationPoint == null) {
            return
        }

        // 使用固定尺寸的小气泡，避免复制提示影响控制台内容布局。
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

        // 尽量把提示显示在当前控制台组件中间偏下的位置；拿不到尺寸时退回点击位置。
        val point = if (component.width > 0) {
            RelativePoint(component, Point(component.width / 2, 18))
        } else {
            hyperlinkLocationPoint
        }

        balloon.show(point, Balloon.Position.below)
    }
}
