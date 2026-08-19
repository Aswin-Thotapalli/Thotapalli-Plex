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
    api(project(":core:playback"))
    implementation(libs.media3.common)
    implementation(libs.coroutines.android)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.hls)
    implementation(libs.media3.ds.okhttp)

    // The Media3 FFmpeg audio decoder, as a locally built AAR, when one is present in libs/.
    //
    // libs.media3.ffmpeg is not used: Google publishes no binary of it to any Maven
    // repository and ships it as source needing an NDK build. That build runs in
    // .github/workflows/ffmpeg-decoder.yml, which commits the resulting AAR into libs/, so
    // no NDK is needed to build the app.
    //
    // This is additive. DefaultRenderersFactory with EXTENSION_RENDERER_MODE_PREFER
    // (see ExoPlayerEngine) finds the FFmpeg renderer by reflection when the AAR is on the
    // classpath and ignores it otherwise, so a checkout without the AAR still builds and
    // unsupported audio simply falls back to a server transcode. See CLAUDE.md sections 8
    // and 10.
    implementation(fileTree("libs") { include("*.aar") })
}
