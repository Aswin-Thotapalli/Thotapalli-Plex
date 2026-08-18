// JVM desktop only. Not multiplatform: libmpv is bound through JNA on Windows.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(providers.gradleProperty("thotapalli.jdk").get().toInt())
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useKotlinTest(libs.versions.kotlin)
        }
    }
}

dependencies {
    api(project(":core:playback"))
    implementation(libs.jna)
    implementation(libs.jna.platform)
}
