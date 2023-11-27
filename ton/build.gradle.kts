plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ton"
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
    implementation(Libs.ton)
    implementation(Libs.AndroidX.security)
    implementation(Libs.Squareup.okhttp)

    implementation(project(Libs.Module.core))
    implementation(project(Libs.Module.tonApi))
}