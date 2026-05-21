package com.example.consolelinks

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project

// IDEA 会通过 plugin.xml 中注册的 consoleFilterProvider 找到这个类。
class ConsoleLinksFilterProvider : ConsoleFilterProvider {
    // 为当前项目创建控制台过滤器，后续每一行控制台输出都会交给该过滤器处理。
    override fun getDefaultFilters(project: Project): Array<Filter> {
        return arrayOf(ConsoleLinksFilter(project))
    }
}
