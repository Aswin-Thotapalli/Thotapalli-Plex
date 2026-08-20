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
                val versionName = providers.gradleProperty("thotapalli.versionName").get()
                val versionCode =
                    providers.gradleProperty("thotapalli.versionCode").orNull?.toLongOrNull()
                if (versionCode == null) {
                    versionName
                } else {
                    val parts = versionName.split(".")
                    val major = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "0"
                    val minor = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "0"
                    "$major.$minor.${versionCode % 65535}"
                }
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
