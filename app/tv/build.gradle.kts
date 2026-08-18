// Android TV and Google TV. A separate subproject from the multiplatform modules because
// Android Gradle Plugin 9 refuses the multiplatform plugin alongside the application plugin.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.thotapalli.plex.tv"
    compileSdk = providers.gradleProperty("thotapalli.compileSdk").get().toInt()

    defaultConfig {
        applicationId = "com.thotapalli.plex"
        minSdk = providers.gradleProperty("thotapalli.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("thotapalli.targetSdk").get().toInt()
        versionCode = providers.gradleProperty("thotapalli.versionCode").get().toInt()
        versionName = providers.gradleProperty("thotapalli.versionName").get()
    }

    buildFeatures {
        compose = true
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
}
