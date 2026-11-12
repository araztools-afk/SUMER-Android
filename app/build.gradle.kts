plugins {
    id("com.android.application")
}

android {
    namespace = "com.araztools.sumer"

    compileSdk = 35

    defaultConfig {
        // Unique package ID so this Live preview installs beside S1.
        applicationId = "com.araztools.sumer.livepreview"
        minSdk = 23
        targetSdk = 28
        versionCode = 2
        versionName = "1.1-live-preview"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
