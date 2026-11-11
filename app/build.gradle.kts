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
        // Target Android 14 explicitly for the first phone test.
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
