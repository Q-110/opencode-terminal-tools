plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "io.github.q110"
version = "0.1.0"

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

        ideaVersion {
            sinceBuild = "253"
        }
    }
}
