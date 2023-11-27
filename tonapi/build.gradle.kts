plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "tonapi"
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
    implementation(Libs.Squareup.moshi)
    implementation(Libs.Squareup.moshiAdapters)
    implementation(Libs.Squareup.okhttp)
}