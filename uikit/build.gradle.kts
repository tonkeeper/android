plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uikit"
    compileSdk = Build.compileSdkVersion

    defaultConfig {
        minSdk = Build.minSdkVersion
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(Libs.AndroidX.core)
    implementation(Libs.AndroidX.activity)
    implementation(Libs.AndroidX.fragment)
    implementation(Libs.AndroidX.appCompat)
    implementation(Libs.AndroidX.splashscreen)
    implementation(Libs.UI.material)
    implementation(Libs.lottie)
}