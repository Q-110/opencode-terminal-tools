plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "io.github.q110"
version = "1.4.3"

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
        id = "io.github.q110.consolelinks"
        name = "Console Links"
        version = project.version.toString()
        description = """
            <p>Console Links 是一个面向 IntelliJ IDEA 的终端/控制台增强插件。</p>
            <p>插件会将输出中的文件引用识别为可点击链接，支持短文件名、相对路径、绝对路径、指定行号和行范围，点击后可快速跳转到对应文件位置。</p>
            <p>除文件跳转外，插件还支持点击复制结构化输出片段，例如方法调用、接口路径、点号链、数组和常见字面量，便于从终端输出中提取关键信息。</p>
        """.trimIndent()
        changeNotes = """
            <ul>
              <li>内容区固定 64 x 28</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "253"
        }
    }
}
