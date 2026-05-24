plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "io.github.q110"
version = "1.9.0"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val platformVersion = providers.gradleProperty("platformVersion").getOrElse("2025.3")
val platformType = providers.gradleProperty("platformType").getOrElse("IU")

dependencies {
    intellijPlatform {
        when (platformType.uppercase()) {
            "IC", "IU" -> intellijIdea(platformVersion)
            "PY"       -> pycharm(platformVersion)
            "WS"       -> webstorm(platformVersion)
            "GO"       -> goland(platformVersion)
            "PS"       -> phpstorm(platformVersion)
            "RM"       -> rubymine(platformVersion)
            "RD"       -> rider(platformVersion)
            "CL"       -> clion(platformVersion)
            "DG"       -> datagrip(platformVersion)
            else       -> intellijIdea(platformVersion)
        }
        bundledPlugin("org.jetbrains.plugins.terminal")
    }
}

intellijPlatform {
    publishing {
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
        channels.set(listOf("default"))
    }

    signing {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }

    pluginConfiguration {
        id = "io.github.q110.opencodeterminaltools"
        name = "OpenCode Terminal Tools"
        version = project.version.toString()
        description = """
            <p>OpenCode Terminal Tools enhances JetBrains IDE terminals and consoles with fast navigation, copy helpers, console error sending, and OpenCode integration.</p>
            <ul>
              <li>File navigation: turns terminal and console file references into clickable links, including short file names, relative paths, absolute paths, line numbers, and line ranges.</li>
              <li>Click-to-copy: recognizes structured output fragments such as method calls, API paths, dotted identifiers, string literals, numbers, and URLs, then copies them to the clipboard with one click.</li>
              <li>Console error sending: adds an inline OpenCode icon to Java/JVM exception lines and sends the current visible exception segment to OpenCode with one click.</li>
              <li>OpenCode bridge: sends editor selections, project tree file or folder paths, and editor tab paths to the OpenCode TUI input area, including support for completing @path input state.</li>
            </ul>
        """.trimIndent()
        changeNotes = """
            <ul>
              <li>Adds inline OpenCode icons for Java/JVM console exception segments.</li>
              <li>Sends only the current visible exception segment, including the exception line and consecutive stack frames.</li>
              <li>Uses the Chinese payload title "控制台错误：" for console error sends.</li>
              <li>Adds a settings toggle for error-to-OpenCode console icons.</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "253"
        }
    }
}
