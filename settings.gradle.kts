// Module structure per CLAUDE.md section 3.
// core/*, ui/* and player/exo are Kotlin Multiplatform libraries.
// app/mobile, app/tv and app/desktop are separate non-multiplatform application subprojects,
// because Android Gradle Plugin 9 does not permit the multiplatform plugin and the Android
// application plugin in the same subproject.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "thotapalli-plex"

include(
    ":core:model",
    ":core:api",
    ":core:session",
    ":core:data",
    ":core:playback",
    ":core:download",
    ":ui:design",
    ":ui:shared",
    ":player:exo",
    ":player:mpv",
    ":app:mobile",
    ":app:tv",
    ":app:desktop",
)
