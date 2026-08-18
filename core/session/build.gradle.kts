plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(providers.gradleProperty("thotapalli.jdk").get().toInt())

    android {
        namespace = "com.thotapalli.plex.core.session"
        compileSdk = providers.gradleProperty("thotapalli.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("thotapalli.minSdk").get().toInt()

        withHostTest {}
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:api"))
            implementation(libs.coroutines.core)
        }

        androidMain.dependencies {
            // EncryptedSharedPreferences for the account token. CLAUDE.md section 5 step 4.
            implementation(libs.androidx.security.crypto)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.browser)
        }

        jvmMain.dependencies {
            // DPAPI through JNA, CryptProtectData scoped to the current user.
            implementation(libs.jna)
            implementation(libs.jna.platform)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}
