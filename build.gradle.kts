plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "io.github.q110"
version = "1.4.4"

kotlin {
    jvmToolchain(17)
}

dependencies {
    intellijPlatform {
        local(providers.gradleProperty("localIdePath").get())
        bundledPlugin("org.jetbrains.plugins.terminal")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "io.github.q110.consolelinks"
        name = "Console Links"
        version = project.version.toString()
        description = """
            <p>Console Links 是一个面向 IntelliJ IDEA 的终端/控制台增强插件，提供三大功能：</p>
            <ul>
              <li>文件跳转 — 将输出中的文件引用识别为可点击链接，支持短文件名、相对路径、绝对路径、行号和行范围，点击跳转到对应文件位置</li>
              <li>点击复制 — 结构化输出片段（方法调用、接口路径、点号链、字面量等）点击即复制到剪贴板</li>
              <li>OpenCode 桥接 — 在编辑器中选中代码，一键发送到 OpenCode TUI 输入区</li>
            </ul>
        """.trimIndent()
        changeNotes = """
            <ul>
              <li>内容区固定 64 x 28</li>
              <li>修复选区 payload 格式：去除 header 与代码间的空行，代码末尾追加换行</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "253"
        }
    }
}
