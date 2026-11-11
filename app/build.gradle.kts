plugins {
    id("com.android.application")
}

android {
    namespace = "com.araztools.sumer"

    compileSdk = 35

    defaultConfig {
        // Separate diagnostic package prevents conflicts with any older SUMER APK
        // that may have been installed with a different signing key.
        applicationId = "com.araztools.sumer.installtest"
        minSdk = 23
        targetSdk = 28
        versionCode = 1
        versionName = "1.0-install-test"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
