plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "io.github.q110"
version = "1.2.0"

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
              <li>新增结构化输出片段点击复制能力，并收窄匹配范围，避免普通英文和普通数字被误识别。</li>
              <li>增强文件引用识别，支持短文件名、相对路径、绝对路径、行号和行范围跳转。</li>
              <li>优化同名文件选择逻辑，最高得分并列时弹出选择对话框。</li>
              <li>新增插件图标，更新插件版本号和发布元信息。</li>
              <li>更新 README 示例，避免包含本地路径或业务数据。</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "253"
        }
    }
}
