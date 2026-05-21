package com.example.consolelinks

// 集中维护控制台文本识别规则，避免正则分散在业务代码中难以调整。
internal object FilterPatterns {
    // 当前插件支持识别为文件引用的扩展名。
    private const val SUPPORTED_EXTENSIONS = "java|kt|kts|js|jsx|ts|tsx|vue|xml|html|css|scss|yml|yaml|properties|sql|md"

    // 只匹配单个文件名，例如 ExampleController.java。
    private const val FILE_NAME_PATTERN = """[A-Za-z_$][A-Za-z0-9_.$-]*\.(?:$SUPPORTED_EXTENSIONS)"""

    // 匹配带目录的路径，兼容 Windows 反斜杠和 Unix 正斜杠。
    private const val PATH_PATTERN = """(?:(?:[A-Za-z]:)?[\\/]|\.{1,2}[\\/])?[A-Za-z0-9_.$-]+(?:[\\/][A-Za-z0-9_.$-]+)+\.(?:$SUPPORTED_EXTENSIONS)"""

    // 匹配文件引用，并可选匹配行号或行范围，例如 Demo.kt:10-20。
    val fileRefPattern = Regex(
        """(?<![\\/A-Za-z0-9_.$-])($PATH_PATTERN|$FILE_NAME_PATTERN)(?::(\d+)(?:-(\d+))?)?(?![\d\w.$-])"""
    )

    // 匹配适合点击复制的结构化片段；顺序越靠前，优先级越高。
    val copyPatterns = listOf(
        // 双花括号内容，例如 {{name}}。
        Regex("""\{\{[^{}\r\n]*[A-Za-z_$][^{}\r\n]*}}"""),
        // 双中括号内容，例如 [[204,147,73]]。
        Regex("""\[\[[^\r\n|]+]]"""),
        // 包含字符串的数组，例如 ["alpha","beta"]。
        Regex("""\[[^\r\n|]*"[^"\r\n]+"[^\r\n|]*]"""),
        // 方法调用，例如 loadData() 或 showPanel(true)。
        Regex("""(?<![\w$])\$?[A-Za-z_$][A-Za-z0-9_$]*\([^()\r\n]*\)"""),
        // 斜杠路径或接口地址，例如 /api/items?name=。
        Regex("""(?<![\w$])/?[A-Za-z0-9_$.-]+(?:/[A-Za-z0-9_$?=&.-]+)+(?![\w$])"""),
        // 点号链或下标访问，例如 self.items[currentIndex]。
        Regex("""(?<![\w$])\$?[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*|\[[A-Za-z_$][A-Za-z0-9_$.]*])+(?![\w$])"""),
        // 常见字面量。
        Regex("""(?<![\w$])(?:null|NaN|true|false)(?![\w$])"""),
        // 普通英文标识符。
        Regex("""(?<![\w$])\$?[A-Za-z_$][A-Za-z0-9_$-]*(?![\w$])"""),
        // 普通数字。
        Regex("""(?<![\w.])\d+(?![\w.])""")
    )
}
