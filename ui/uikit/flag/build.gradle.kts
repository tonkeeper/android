plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = Build.namespacePrefix("uikit.flag")
    compileSdk = Build.compileSdkVersion
    defaultConfig {
        minSdk = Build.minSdkVersion
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
