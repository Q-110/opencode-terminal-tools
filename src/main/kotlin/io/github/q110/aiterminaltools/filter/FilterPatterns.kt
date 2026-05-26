// 控制台文本解析用正则表达式常量
package io.github.q110.aiterminaltools.filter

internal object FilterPatterns {
    // 匹配文件引用，支持行号和行范围：Main.java:10-20
    fun fileRefPattern(extensions: Set<String>): Regex {
        val extensionPattern = extensions.joinToString("|") { Regex.escape(it) }
        val fileNamePattern = """(?:[A-Za-z_$][A-Za-z0-9_.$-]*\.(?:$extensionPattern)|\.(?:$extensionPattern))"""
        val pathPrefixPattern = """(?:(?:[A-Za-z]:)?[\\/]|\.{1,2}[\\/])?(?:[A-Za-z0-9_.$-]+[\\/])+"""
        val pathPattern = """$pathPrefixPattern$fileNamePattern"""
        return Regex(
            """(?<![\\/A-Za-z0-9_.$-])($pathPattern|$fileNamePattern)(?::(\d+)(?:-(\d+))?)?(?![\d\w.$-])""",
            RegexOption.IGNORE_CASE
        )
    }

    // 匹配 AI 终端 @路径引用：@src/main/java/A.java:10
    val atPathRefPattern = Regex("""(?<![\w$.-])@([A-Za-z0-9_.$-]+(?:[\\/][A-Za-z0-9_.$-]+)*)(?::(\d+)(?:-(\d+))?)?(?![\d\w.$-])""")
    // 点击复制模式列表，按优先级从高到低排列
    val copyPatterns = listOf(
        Regex("""\{\{[^{}\r\n]*[A-Za-z_$][^{}\r\n]*}}"""),
        Regex("""\[\[[^\r\n|]+]]"""),
        Regex("""\[[^\r\n|]*"[^"\r\n]+"[^\r\n|]*]"""),
        Regex("""(?<![\w$])\$?[A-Za-z_$][A-Za-z0-9_$]*\([^()\r\n]*\)"""),
        Regex("""(?<![\w$])/?[A-Za-z0-9_$.-]+(?:/[A-Za-z0-9_$?=&.-]+)+(?![\w$])"""),
        Regex("""(?<![\w$])\$?[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*|\[[A-Za-z_$][A-Za-z0-9_$.]*])+(?![\w$])"""),
        Regex("""(?<![\w$])(?:null|NaN|true|false)(?![\w$])"""),
        Regex("""(?<![\w$])\$?[A-Za-z_$][A-Za-z0-9_$-]*(?![\w$])"""),
        Regex("""(?<![\w.])\d+(?![\w.])""")
    )
}
