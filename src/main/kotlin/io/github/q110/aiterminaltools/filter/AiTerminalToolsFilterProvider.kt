// ConsoleFilterProvider 实现 — 向控制台/终端注册核心 Filter
package io.github.q110.aiterminaltools.filter

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project

class AiTerminalToolsFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> {
        return arrayOf(AiTerminalToolsFilter(project))
    }
}
