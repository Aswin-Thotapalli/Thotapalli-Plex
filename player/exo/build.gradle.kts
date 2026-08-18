// Android only. Not multiplatform: Media3 exists on no other target.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.thotapalli.plex.player.exo"
    compileSdk = providers.gradleProperty("thotapalli.compileSdk").get().toInt()
    defaultConfig {
        minSdk = providers.gradleProperty("thotapalli.minSdk").get().toInt()
    }
}

kotlin {
    jvmToolchain(providers.gradleProperty("thotapalli.jdk").get().toInt())
}

dependencies {
    implementation(project(":core:playback"))
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.hls)
    implementation(libs.media3.ds.okhttp)
    // libs.media3.ffmpeg is deliberately absent. See gradle/libs.versions.toml.
}
