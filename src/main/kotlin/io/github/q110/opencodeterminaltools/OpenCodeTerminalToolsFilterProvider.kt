package io.github.q110.opencodeterminaltools

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project

class OpenCodeTerminalToolsFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> {
        return arrayOf(OpenCodeTerminalToolsFilter(project))
    }
}
