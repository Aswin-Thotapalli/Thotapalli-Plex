// Windows. A Compose Desktop application, not multiplatform, per CLAUDE.md section 3.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(providers.gradleProperty("thotapalli.jdk").get().toInt())
}

dependencies {
    implementation(project(":ui:shared"))
    implementation(project(":ui:design"))
    implementation(project(":core:data"))
    implementation(project(":core:session"))
    implementation(project(":core:playback"))
    implementation(project(":core:download"))
    implementation(project(":player:mpv"))
    implementation(compose.desktop.currentOs)
    // For borderless full screen: hide the Windows taskbar via the Win32 shell APIs.
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)
    // AppViewModel extends androidx.lifecycle.ViewModel, so its supertype has to be on the
    // desktop compile classpath too.
    implementation(libs.compose.lifecycle.viewmodel)
    implementation(libs.compose.lifecycle.runtime)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    // The single-window player owns an OpenGL context (mpv render API composited with the Compose
    // overlay via Skia). LWJGL provides the GL bindings and the AWT-embedded context; glfw is only
    // used by the render-pipeline proof harness.
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.opengl)
    implementation(libs.lwjgl.glfw)
    implementation(libs.lwjgl3.awt)
    val lwjglVersion = libs.versions.lwjgl.get()
    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:natives-windows")
}

/**
 * The phase 2 command line harness from CLAUDE.md section 16. Not part of the shipped
 * application; it exists so each phase 2 step has an observable output.
 *
 *   gradlew :app:desktop:harness --args="signin"
 */
/** The phase 4 design gallery: every token, every component, the responsive grid. */
val gallery by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Opens the design gallery window."
    mainClass.set("com.thotapalli.plex.desktop.GalleryKt")
    classpath = sourceSets["main"].runtimeClasspath
    // Rendered at a fixed density so a captured screenshot is comparable between machines.
    systemProperty("skiko.win.exception.logger.enabled", "true")
}

/** Proof harness for the single-window render pipeline (mpv render API into our own GL context). */
val renderProbe by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Opens a GL window and has mpv render a test pattern into it via the render API."
    mainClass.set("com.thotapalli.plex.desktop.RenderProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

/** Proof harness for the SHIPPED path: real MpvPlayerEngine (render mode) into an AWTGLCanvas. */
val glCanvasProbe by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Real engine + AWTGLCanvas + Compose scene composite, read back to a PNG."
    mainClass.set("com.thotapalli.plex.desktop.GlCanvasProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
}

/** Runtime harness for the shipped single-window VideoSurface (SwingPanel + AWTGLCanvas). */
val playerHarness by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Opens a real Compose window hosting the shipped VideoSurface playing a test pattern."
    mainClass.set("com.thotapalli.plex.desktop.PlayerHarnessKt")
    classpath = sourceSets["main"].runtimeClasspath
}

val harness by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the Plex account and server access harness."
    mainClass.set("com.thotapalli.plex.desktop.harness.HarnessKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}

compose.desktop {
    application {
        mainClass = "com.thotapalli.plex.desktop.MainKt"

        // Do NOT minify the desktop release. ProGuard on this app strips classes the runtime needs
        // reflectively — the Ktor engine, the SQLite JDBC driver, JNA-bound natives, the libmpv
        // bindings, even the Compose entry point — which the packaged launcher reports as
        // "Failed to launch JVM". The MSI is a little larger unminified, but it actually runs. (The
        // Haze/Skiko unresolved-reference warning that needed compose-desktop.pro was only a symptom
        // of running ProGuard at all; with ProGuard off it no longer applies.)
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        // Faster cold start: stop JIT at the cheap tier (UI work is light; heavy decode is native
        // in libmpv) and use the serial collector so the JVM spins up with fewer threads.
        jvmArgs += listOf("-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1")

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            // Bundle every JDK module in the runtime. The default jlink image is minimal and
            // left out modules the dependencies need at runtime — java.net.http for Ktor's Java
            // engine, java.sql for the SQLite driver — which crashed the app on launch with a
            // NoClassDefFoundError the packaged launcher reports as "Failed to launch JVM".
            includeAllModules = true
            packageName = "Thotapalli Plex"
            // An MSI only upgrades in place when its ProductVersion increases, and every build
            // ships the same versionName (0.1.0). So derive major.minor from the version name
            // and put the monotonic release version code into the build field. MSI allows
            // major.minor.build with each field <= 65535, so the epoch-based code is folded into
            // that range; a plain versionName is used when no code is supplied (local builds).
            packageVersion = run {
                // A strictly-monotonic Windows ProductVersion so every build upgrades in place. MSI
                // caps each field (major<=255, minor<=255, build<=65535), so an absolute timestamp
                // will not fit a single field: encode minutes-since-2026 across the minor and build
                // fields (headroom to ~2057), keeping the app major from the version name. Minutes,
                // not hours, so builds only minutes apart still get a higher version. See the
                // matching auto versionCode (raw epoch seconds) in the Android modules.
                val versionName = providers.gradleProperty("thotapalli.versionName").get()
                val major = versionName.split(".").getOrNull(0)?.takeIf { it.isNotBlank() } ?: "0"
                val minutes = ((System.currentTimeMillis() / 1000L - 1_767_225_600L) / 60L)
                    .coerceAtLeast(1L)
                val hi = (minutes / 65536L).coerceIn(0L, 255L)
                val lo = minutes % 65536L
                "$major.$hi.$lo"
            }
            vendor = "Thotapalli"

            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
                // Stable so every MSI upgrades the previous install rather than sitting
                // beside it. Generated once; never regenerate.
                upgradeUuid = "6E5F3A21-9C42-4B7E-8D10-2F4A6B8C1D3E"
                menuGroup = "Thotapalli Plex"
                dirChooser = true
            }

            // libmpv-2.dll is bundled from app/desktop/native/windows-x64 so the user
            // performs no separate install step. See CLAUDE.md section 4.
            appResourcesRootDir.set(layout.projectDirectory.dir("native"))
        }
    }
}
