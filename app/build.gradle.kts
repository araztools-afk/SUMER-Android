plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.araztools.sumer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.araztools.sumer"
        minSdk = 23
        // Legacy Bluetooth permission model keeps Classic SPP compatible
        // from Android 6 through modern Android versions for this sideloaded build.
        targetSdk = 28
        versionCode = 3
        versionName = "0.3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
