plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "io.github.q110"
version = "1.7.0"

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
              <li>OpenCode 桥接 — 将编辑器选区、项目树文件/文件夹路径和编辑器标签页路径发送到 OpenCode TUI 输入区</li>
            </ul>
        """.trimIndent()
        changeNotes = """
            <ul>
              <li>新增项目树和编辑器标签页路径发送到 OpenCode，路径使用仓库根相对路径并带 @ 前缀</li>
              <li>新增 @路径 控制台链接识别，支持点击文件跳转和文件夹定位</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "253"
        }
    }
}
