// JVM desktop only. Not multiplatform: libmpv is bound through JNA on Windows.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(providers.gradleProperty("thotapalli.jdk").get().toInt())
}

dependencies {
    implementation(project(":core:playback"))
    implementation(libs.jna)
    implementation(libs.jna.platform)
}
