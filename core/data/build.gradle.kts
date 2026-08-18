plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(providers.gradleProperty("thotapalli.jdk").get().toInt())

    // DatabaseDriverFactory is an expect/actual class, deliberately: the Android and JDBC
    // drivers take different constructor arguments, so an interface would force a
    // factory for the factory. The feature is marked Beta, so opt in rather than carry
    // the warning on every build.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.thotapalli.plex.core.data"
        compileSdk = providers.gradleProperty("thotapalli.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("thotapalli.minSdk").get().toInt()

        withHostTest {}
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:api"))
            implementation(project(":core:session"))
            api(project(":core:download"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.coroutines.core)
        }

        androidMain.dependencies {
            implementation(libs.sqldelight.android)
        }

        jvmMain.dependencies {
            implementation(libs.sqldelight.jvm)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }

        jvmTest.dependencies {
            // An in-memory JDBC driver, so repository tests exercise the real schema
            // rather than a stand-in for it.
            implementation(libs.sqldelight.jvm)
        }
    }
}

sqldelight {
    databases {
        create("PlexDatabase") {
            packageName.set("com.thotapalli.plex.core.data.db")
            // The cache is safe to delete and is never the source of truth for anything
            // the server also knows, so a schema change drops and rebuilds rather than
            // migrating. See CLAUDE.md section 7.
            verifyMigrations.set(false)
        }
    }
}
