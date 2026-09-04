plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(providers.gradleProperty("thotapalli.jdk").get().toInt())

    android {
        namespace = "com.thotapalli.plex.core.model"
        compileSdk = providers.gradleProperty("thotapalli.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("thotapalli.minSdk").get().toInt()

        withHostTest {}
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            // For the Diagnostics recorder's observable event flow. core:model stays otherwise
            // dependency-free; coroutines-core is a foundational, platform-neutral primitive.
            api(libs.coroutines.core)
        }
    }
}
