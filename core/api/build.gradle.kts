plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
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
            api(project(":core:model"))
            // RangedDownloadSource implements DownloadTransport, which is declared there.
            // api rather than implementation because that type is in this module's public
            // API. :core:download deliberately does not depend back on this module.
            api(project(":core:download"))
            implementation(libs.ktor.core)
            implementation(libs.ktor.contentneg)
            implementation(libs.ktor.json)
            implementation(libs.ktor.logging)
            implementation(libs.serialization.json)
            implementation(libs.coroutines.core)
        }

        androidMain.dependencies {
            implementation(libs.ktor.okhttp)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.java)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.mock)
            implementation(libs.coroutines.test)
        }
    }
}
