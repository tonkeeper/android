plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = Build.namespacePrefix("uikit.list")
    compileSdk = Build.compileSdkVersion
    defaultConfig {
        minSdk = Build.minSdkVersion
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(Dependence.AndroidX.recyclerView)
    implementation(Dependence.AndroidX.lifecycle)
}
