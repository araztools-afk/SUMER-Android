plugins {
    id("com.android.application")
}

android {
    namespace = "com.araztools.sumer"

    compileSdk = 35

    defaultConfig {
        // Keep a separate package id so this preview can install alongside the earlier S1 prototype.
        applicationId = "com.araztools.sumer.livepreview"
        minSdk = 23
        targetSdk = 28
        versionCode = 3
        versionName = "SUMER1"
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = rootProject.file(".github/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}