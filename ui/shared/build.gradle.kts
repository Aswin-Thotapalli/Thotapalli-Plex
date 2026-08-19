plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(providers.gradleProperty("thotapalli.jdk").get().toInt())

    android {
        namespace = "com.thotapalli.plex.ui.shared"
        compileSdk = providers.gradleProperty("thotapalli.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("thotapalli.minSdk").get().toInt()

        withHostTest {}
    }

    jvm()


    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.lifecycle.viewmodel)
            implementation(libs.compose.lifecycle.runtime)
            implementation(libs.compose.navigation)
            implementation(libs.coroutines.core)
            implementation(project(":ui:design"))
            implementation(project(":core:model"))
            implementation(project(":core:data"))
            implementation(project(":core:playback"))
            implementation(project(":core:session"))
            implementation(project(":core:download"))
            implementation(libs.coil.compose)
            implementation(libs.coil.network)
        }

        androidMain.dependencies {
            // The Media3 engine and its surface. player:exo depends only on core:playback,
            // never upward, so this stays within the dependency direction in CLAUDE.md
            // section 3. media3.common carries the UnstableApi opt-in marker referenced when
            // constructing ExoPlayerEngine.
            implementation(project(":player:exo"))
            implementation(libs.media3.common)
        }

        jvmMain.dependencies {
            // The libmpv engine for Windows. Also depends only on core:playback.
            implementation(project(":player:mpv"))
            // JNA, to read the AWT canvas's native window handle so mpv renders inside the
            // application window rather than opening its own.
            implementation(libs.jna)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}
