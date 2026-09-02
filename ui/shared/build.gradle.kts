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
            implementation(libs.haze)
            // QR code painter for the television sign-in screen. CLAUDE.md section 14 item 1.
            implementation(libs.qrose)
            implementation(project(":core:model"))
            implementation(project(":core:data"))
            implementation(project(":core:playback"))
            implementation(project(":core:session"))
            implementation(project(":core:download"))
            implementation(libs.coil.compose)
            implementation(libs.coil.network)
            // okio.Path, for the Coil disk-cache directory in ImageLoaderSetup. Coil pulls okio in
            // transitively, but the directory is named directly here, so it is declared explicitly.
            implementation(libs.okio)
        }

        androidMain.dependencies {
            // The Media3 engine and its surface. player:exo depends only on core:playback,
            // never upward, so this stays within the dependency direction in CLAUDE.md
            // section 3. media3.common carries the UnstableApi opt-in marker referenced when
            // constructing ExoPlayerEngine.
            implementation(project(":player:exo"))
            implementation(libs.media3.common)
            // media3-ui supplies SubtitleView and CaptionStyleCompat, used by the Android
            // VideoSurface to render Media3's cues above the bare SurfaceView. player:exo keeps
            // it as an implementation dependency, so it is not transitive and is declared here
            // where the SubtitleView is constructed. See CLAUDE.md sections 8 and 12.
            implementation(libs.media3.ui)
            implementation(libs.kyant.backdrop)
        }

        jvmMain.dependencies {
            // The libmpv engine for Windows. Also depends only on core:playback.
            implementation(project(":player:mpv"))
            // JNA, to read the AWT canvas's native window handle so mpv renders inside the
            // application window rather than opening its own.
            implementation(libs.jna)
            // The single-window desktop player owns an OpenGL context (an AWT-embedded LWJGL
            // context) into which mpv renders (render API) and the Compose overlay is composited
            // via Skia — one window, hardware throughout. See DesktopGlPlayer.
            implementation(libs.lwjgl.core)
            implementation(libs.lwjgl.opengl)
            implementation(libs.lwjgl3.awt)
            val lwjglVersion = libs.versions.lwjgl.get()
            runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:natives-windows")
            runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:natives-windows")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}

// The brand mark (brand/Files artwork) lives in commonMain/composeResources/drawable and is
// reached through the generated Res class below, shared by the sign-in logo and the launch splash.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.thotapalli.plex.ui.shared.resources"
    generateResClass = always
}
