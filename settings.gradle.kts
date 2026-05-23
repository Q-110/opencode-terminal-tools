pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        ivy {
            name = "Local IntelliJ Platform Artifacts"
            val localPlatformArtifacts = file(".intellijPlatform/localPlatformArtifacts").invariantSeparatorsPath
            ivyPattern("$localPlatformArtifacts/[revision]/[organization]-[module]-[revision].[ext]")
            artifactPattern("/[artifact]")
            artifactPattern("c:/[artifact]")
            artifactPattern("d:/[artifact]")
            artifactPattern("e:/[artifact]")
            metadataSources {
                ivyDescriptor()
                artifact()
            }
        }
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        maven("https://cache-redirector.jetbrains.com/plugins.jetbrains.com/maven")
    }
}

rootProject.name = "opencode-terminal-tools"
