plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "io.github.q110"
version = "1.8.1"

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
            <p>OpenCode Terminal Tools enhances JetBrains IDE terminals and consoles with fast navigation, copy helpers, and OpenCode integration.</p>
            <ul>
              <li>File navigation: turns terminal and console file references into clickable links, including short file names, relative paths, absolute paths, line numbers, and line ranges.</li>
              <li>Click-to-copy: recognizes structured output fragments such as method calls, API paths, dotted identifiers, string literals, numbers, and URLs, then copies them to the clipboard with one click.</li>
              <li>OpenCode bridge: sends editor selections, project tree file or folder paths, and editor tab paths to the OpenCode TUI input area, including support for completing @path input state.</li>
            </ul>
        """.trimIndent()
        changeNotes = """
            <ul>
              <li>Registers context menu actions dynamically so they stay at the top of the relevant menus regardless of plugin loading order.</li>
              <li>Increases the OpenCode trailing-space send delay to avoid losing spaces while the terminal is still processing input.</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "253"
        }
    }
}
