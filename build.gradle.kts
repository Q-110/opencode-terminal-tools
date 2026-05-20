plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "com.example"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    intellijPlatform {
        local("E:/IntelliJ IDEA 2025.3.4")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.example.opencodelinks"
        name = "Opencode Short File Links"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "253"
        }
    }
}
