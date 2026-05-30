// "显示上次 AI Turn Diff" 动作
package io.github.q110.aiterminaltools.monitor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

class ShowLastAiTurnDiffAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabledAndVisible = project != null &&
            project.service<AiTurnDiffPresenter>().lastTurn != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        project.service<AiTurnDiffPresenter>().showLastDiff()
    }
}
