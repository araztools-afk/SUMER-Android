plugins {
    id("com.android.application")
}

android {
    namespace = "com.araztools.sumer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.araztools.sumer"
        minSdk = 23
        targetSdk = 28
        versionCode = 5
        versionName = "0.4.1-diagnostic"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
