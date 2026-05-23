// 控制台文本解析用正则表达式常量
package io.github.q110.opencodeterminaltools.filter

internal object FilterPatterns {
    private const val SUPPORTED_EXTENSIONS = "java|kt|kts|js|jsx|ts|tsx|vue|xml|html|css|scss|yml|yaml|properties|sql|md"
    private const val FILE_NAME_PATTERN = """[A-Za-z_$][A-Za-z0-9_.$-]*\.(?:$SUPPORTED_EXTENSIONS)"""
    private const val PATH_PATTERN = """(?:(?:[A-Za-z]:)?[\\/]|\.{1,2}[\\/])?[A-Za-z0-9_.$-]+(?:[\\/][A-Za-z0-9_.$-]+)+\.(?:$SUPPORTED_EXTENSIONS)"""
    // 匹配文件引用，支持行号和行范围：Main.java:10-20
    val fileRefPattern = Regex("""(?<![\\/A-Za-z0-9_.$-])($PATH_PATTERN|$FILE_NAME_PATTERN)(?::(\d+)(?:-(\d+))?)?(?![\d\w.$-])""")
    // 匹配 OpenCode @路径引用：@src/main/java/A.java:10
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
