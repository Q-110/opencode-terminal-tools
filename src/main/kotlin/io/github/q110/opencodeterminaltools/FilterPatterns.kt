package io.github.q110.opencodeterminaltools

internal object FilterPatterns {
    private const val SUPPORTED_EXTENSIONS = "java|kt|kts|js|jsx|ts|tsx|vue|xml|html|css|scss|yml|yaml|properties|sql|md"
    private const val FILE_NAME_PATTERN = """[A-Za-z_$][A-Za-z0-9_.$-]*\.(?:$SUPPORTED_EXTENSIONS)"""
    private const val PATH_PATTERN = """(?:(?:[A-Za-z]:)?[\\/]|\.{1,2}[\\/])?[A-Za-z0-9_.$-]+(?:[\\/][A-Za-z0-9_.$-]+)+\.(?:$SUPPORTED_EXTENSIONS)"""
    val fileRefPattern = Regex("""(?<![\\/A-Za-z0-9_.$-])($PATH_PATTERN|$FILE_NAME_PATTERN)(?::(\d+)(?:-(\d+))?)?(?![\d\w.$-])""")
    val atPathRefPattern = Regex("""(?<![\w$.-])@([A-Za-z0-9_.$-]+(?:[\\/][A-Za-z0-9_.$-]+)*)(?::(\d+)(?:-(\d+))?)?(?![\d\w.$-])""")
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
