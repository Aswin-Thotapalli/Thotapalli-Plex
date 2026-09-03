// Android TV and Google TV. A separate subproject from the multiplatform modules because
// Android Gradle Plugin 9 refuses the multiplatform plugin alongside the application plugin.
// Imported rather than written out in full: the Kotlin DSL binds `java` to the Java plugin
// extension, so a fully qualified java.io.File does not resolve inside a build script.
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// --- Release signing, CLAUDE.md section 17 point 1 -------------------------------------------
// The same keystore and the same key as app/mobile. Phone and television are separate artefacts
// sharing one application id, so signing them with one key is what lets either one upgrade an
// installation of the other rather than being refused.
//
// Resolution order is environment first, then a properties file. CI passes the material as
// environment variables so nothing secret is ever written into the working tree; a developer
// signing locally points thotapalli.keystoreProperties at a file outside the checkout, or drops
// keystore.properties in the project root, which .gitignore already covers.
//
// When nothing resolves, no release signing config is declared at all, which keeps a clean
// checkout with no keystore able to assemble debug builds.
//
// Duplicated from app/mobile/build.gradle.kts. Sharing it would need a convention plugin in
// buildSrc, which this build does not have.
val keystoreProperties = Properties().apply {
    val candidates = listOfNotNull(
        providers.gradleProperty("thotapalli.keystoreProperties").orNull?.let { File(it) },
        rootProject.file("keystore.properties"),
    )
    candidates.firstOrNull { it.isFile }?.inputStream()?.use { load(it) }
}

fun signingMaterial(environmentName: String, propertyName: String): String? =
    (providers.environmentVariable(environmentName).orNull ?: keystoreProperties.getProperty(propertyName))
        ?.takeIf { it.isNotBlank() }

val releaseKeystore: File? = signingMaterial("THOTAPALLI_KEYSTORE_FILE", "storeFile")
    ?.let { path -> File(path).takeIf { it.isAbsolute } ?: rootProject.file(path) }
    ?.takeIf { it.isFile }

android {
    namespace = "com.thotapalli.plex.tv"
    compileSdk = providers.gradleProperty("thotapalli.compileSdk").get().toInt()

    defaultConfig {
        applicationId = "com.thotapalli.plex"
        minSdk = providers.gradleProperty("thotapalli.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("thotapalli.targetSdk").get().toInt()
        // Auto-incrementing versionCode: raw UNIX epoch seconds, matched to the phone module.
        // See app/mobile for the rationale (above the existing epoch-seconds bundle on the track,
        // strictly increasing, fits a signed Int, under Play's 2.1B ceiling until ~2036).
        versionCode = (System.currentTimeMillis() / 1000L).toInt()
        versionName = providers.gradleProperty("thotapalli.versionName").get()
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingMaterial("THOTAPALLI_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingMaterial("THOTAPALLI_KEY_ALIAS", "keyAlias")
                keyPassword = signingMaterial("THOTAPALLI_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // Null when no keystore resolved, which leaves the release build unsigned rather
            // than failing configuration for everyone who has no keystore.
            signingConfig = signingConfigs.findByName("release")

            // R8: shrink and obfuscate the release. See proguard-rules.pro for the app-specific
            // keeps (reflection, the FFmpeg extension renderer); the libraries ship the rest.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        // VERSION_NAME is sent as X-Plex-Version on every request. See CLAUDE.md section 5.
        buildConfig = true
    }

    // See the matching note in app/mobile: the KMP-library plugin used by ui:shared / ui:design does
    // not package their composeResources into consuming Android apps, so we copy the brand mark,
    // splash frames and fonts into this app's assets at the exact runtime path and register it.
    sourceSets.getByName("main").assets.srcDir(
        layout.buildDirectory.dir("generated/composeResourcesAssets").get().asFile,
    )
}

// Copies ui:shared and ui:design composeResources into this app's assets, laid out as the Compose
// resource reader expects: composeResources/<packageOfResClass>/<category>/<file>.
val copyComposeResources by tasks.registering(Copy::class) {
    into(layout.buildDirectory.dir("generated/composeResourcesAssets"))
    from(rootProject.file("ui/shared/src/commonMain/composeResources")) {
        into("composeResources/com.thotapalli.plex.ui.shared.resources")
    }
    from(rootProject.file("ui/design/src/commonMain/composeResources")) {
        into("composeResources/com.thotapalli.plex.ui.design.resources")
    }
}

tasks.matching { it.name == "preBuild" || (it.name.startsWith("merge") && it.name.endsWith("Assets")) }
    .configureEach { dependsOn(copyComposeResources) }

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
    implementation(project(":player:exo"))
    // PlaybackService hosts the engine's MediaSession for background / lock-screen playback (#2).
    implementation(libs.media3.session)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.lifecycle.viewmodel)
    implementation(libs.compose.lifecycle.runtime)
    implementation(libs.coroutines.android)
    // Television focus and surface treatments. See CLAUDE.md section 13.
    implementation(libs.androidx.tv.material)
}
