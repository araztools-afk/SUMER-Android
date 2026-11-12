plugins {
    id("com.android.application")
}

android {
    namespace = "com.araztools.sumer"

    compileSdk = 35

    defaultConfig {
        // Separate package keeps the successfully installed S1 baseline intact.
        applicationId = "com.araztools.sumer.matrix"
        minSdk = 23
        targetSdk = 28
        versionCode = 2
        versionName = "1.1-matrix-single-channel"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
