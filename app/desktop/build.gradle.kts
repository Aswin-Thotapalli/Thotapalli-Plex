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
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)
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
            packageName = "Thotapalli Plex"
            packageVersion = providers.gradleProperty("thotapalli.versionName").get()
            vendor = "Thotapalli"

            windows {
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
