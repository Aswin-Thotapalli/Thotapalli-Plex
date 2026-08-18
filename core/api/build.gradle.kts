plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(providers.gradleProperty("thotapalli.jdk").get().toInt())

    android {
        namespace = "com.thotapalli.plex.core.api"
        compileSdk = providers.gradleProperty("thotapalli.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("thotapalli.minSdk").get().toInt()

        withHostTest {}
    }

    jvm()


    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(libs.ktor.core)
            implementation(libs.ktor.contentneg)
            implementation(libs.ktor.json)
            implementation(libs.ktor.logging)
        }

        androidMain.dependencies {
            implementation(libs.ktor.okhttp)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.java)
        }
    }
}
