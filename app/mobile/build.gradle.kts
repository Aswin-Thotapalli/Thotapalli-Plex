// Android phone and tablet. A separate subproject from the multiplatform modules because
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
// One keystore, generated once and kept outside this repository, signing both the phone and the
// television artefact with the same key so an upgrade installs over the previous version rather
// than being refused as a different application.
//
// Resolution order is environment first, then a properties file. CI passes the material as
// environment variables so nothing secret is ever written into the working tree; a developer
// signing locally points thotapalli.keystoreProperties at a file outside the checkout, or drops
// keystore.properties in the project root, which .gitignore already covers.
//
// When nothing resolves, no release signing config is declared at all. That is deliberate: a
// clean checkout with no keystore must still assemble debug builds, and pull request CI has no
// access to the secrets.
//
// Duplicated in app/tv/build.gradle.kts. Sharing it would need a convention plugin in buildSrc,
// which this build does not have; two copies of twenty lines is the smaller cost.
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
    namespace = "com.thotapalli.plex.mobile"
    compileSdk = providers.gradleProperty("thotapalli.compileSdk").get().toInt()

    defaultConfig {
        applicationId = "com.thotapalli.plex"
        minSdk = providers.gradleProperty("thotapalli.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("thotapalli.targetSdk").get().toInt()
        versionCode = providers.gradleProperty("thotapalli.versionCode").get().toInt()
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
        }
    }

    buildFeatures {
        compose = true
        // VERSION_NAME is sent as X-Plex-Version on every request. See CLAUDE.md section 5.
        buildConfig = true
    }
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
    implementation(project(":player:exo"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.lifecycle.viewmodel)
    implementation(libs.compose.lifecycle.runtime)
    implementation(libs.coroutines.android)
}
