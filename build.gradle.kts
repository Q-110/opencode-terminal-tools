plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "io.github.q110"
version = "1.3.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    intellijPlatform {
        local(providers.gradleProperty("localIdePath").get())
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.q110.opencodelinks"
        name = "Opencode Short File Links"
        version = project.version.toString()
        description = """
            <p>Opencode Short File Links 是一个面向 IntelliJ IDEA 的终端/控制台辅助插件。</p>
            <p>插件会将输出中的文件引用识别为可点击链接，支持短文件名、相对路径、绝对路径、指定行号和行范围，点击后可快速跳转到对应文件位置。</p>
            <p>除文件跳转外，插件还支持点击复制结构化输出片段，例如方法调用、接口路径、点号链、数组和常见字面量，便于从 opencode 输出中提取关键信息。</p>
        """.trimIndent()
        changeNotes = """
            <ul>
              <li>恢复宽泛复制匹配，普通英文标识符和普通数字也可点击复制。</li>
              <li>复制链接不添加额外视觉样式，尽量保持终端原始显示效果。</li>
              <li>保持文件跳转链接优先，文件引用仍执行跳转。</li>
              <li>拆分内部实现结构，降低后续维护成本。</li>
              <li>更新 README，说明当前复制匹配范围和样式行为。</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "253"
        }
    }
}
