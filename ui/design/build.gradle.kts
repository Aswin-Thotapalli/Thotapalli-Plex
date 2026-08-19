plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(providers.gradleProperty("thotapalli.jdk").get().toInt())

    android {
        namespace = "com.thotapalli.plex.ui.design"
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
            implementation(libs.compose.ui.tooling.preview)
            // Bundled fonts (Inter, Sora) served through the generated Res class. See Fonts.kt.
            implementation(libs.compose.components.resources)
        }
    }
}

// The bundled typefaces live in src/commonMain/composeResources/font and are reached through a
// generated Res class. Pin its package so Fonts.kt can import it deterministically.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.thotapalli.plex.ui.design.resources"
    generateResClass = always
}
