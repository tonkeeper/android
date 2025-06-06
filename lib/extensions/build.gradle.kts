plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    kotlin("plugin.serialization")
}

android {
    namespace = Build.namespacePrefix("extensions")
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
    implementation(Dependence.KotlinX.coroutines)
    implementation(Dependence.KotlinX.serialization)
    implementation(Dependence.KotlinX.serializationJSON)
    implementation(Dependence.Koin.core)
    implementation(Dependence.AndroidX.core)
    implementation(Dependence.AndroidX.security)
    implementation(project(Dependence.UIKit.core))
    implementation(project(Dependence.Lib.icu))
    implementation(project(Dependence.Lib.base64))
    implementation(Dependence.GooglePlay.installreferrer)
    implementation("com.google.android.gms:play-services-base:18.6.0")
}
