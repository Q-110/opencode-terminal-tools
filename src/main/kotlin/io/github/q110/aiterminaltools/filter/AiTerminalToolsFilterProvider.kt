// ConsoleFilterProvider 实现 — 向控制台/终端注册核心 Filter
package io.github.q110.aiterminaltools.filter

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project

class AiTerminalToolsFilterProvider : ConsoleFilterProvider {
    /** 每个项目创建独立 Filter，便于按项目索引解析文件路径 */
    override fun getDefaultFilters(project: Project): Array<Filter> {
        return arrayOf(AiTerminalToolsFilter(project))
    }
}
