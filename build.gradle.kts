plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "io.github.q110"
version = "0.1.2"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val platformVersion = providers.gradleProperty("platformVersion").getOrElse("2025.1")
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
        id = "io.github.q110.aiterminaltools"
        name = "AI Terminal Tools"
        version = project.version.toString()
        description = """
            <p>AI Terminal Tools enhances JetBrains IDE terminals and consoles with fast navigation, click-to-copy, AI terminal sending, OpenCode / Claude Code launch actions, AI Turn Diff, and commit message generation.</p>
            <ul>
              <li>File navigation: turns terminal and console file references into clickable links, including short file names, relative/absolute paths, line numbers, and line ranges. Uses IntelliJ project index for accurate matching across all project types.</li>
              <li>Click-to-copy: recognizes structured terminal output fragments such as method calls, API paths, dotted identifiers, strings, numbers, and URLs, then copies them to the clipboard with one click. Works across Classic, Reworked, and Frontend terminal engines.</li>
              <li>Console error sending: adds an inline AI terminal icon to multi-language exception lines (Java, Python, JS/TS, Go, Rust, Ruby, C/C++) and sends the current visible error segment to the active terminal.</li>
              <li>AI terminal bridge: sends editor selections, project tree file/folder paths, editor tab paths, and dragged files to the active terminal input area, including support for completing @path input state.</li>
              <li>Launch actions: starts OpenCode or Claude Code in dedicated terminal tabs.</li>
              <li>AI Turn Diff: shows the files changed by each OpenCode or Claude Code turn in an IntelliJ diff window.</li>
              <li>Commit message generation: adds a commit toolbar action that uses OpenCode or Claude Code to generate concise commit message bullets from the files checked in the Commit panel, with configurable AI tool, model, and extra prompts.</li>
            </ul>
        """.trimIndent()
        changeNotes = """
            <ul>
              <li>Adds AI Turn Diff for OpenCode and Claude Code sessions.</li>
              <li>OpenCode turn diffs now use official session lifecycle events to avoid premature diff popups.</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "251"
        }
    }
}
