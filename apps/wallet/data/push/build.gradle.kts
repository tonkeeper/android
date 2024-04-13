plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("wallet.data.push")
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
    implementation(Dependence.Koin.core)
    implementation(Dependence.Squareup.okhttp)
    
    implementation(platform(Dependence.Firebase.bom))
    implementation(Dependence.Firebase.messaging)

    implementation(project(Dependence.Wallet.api))
    implementation(project(Dependence.Wallet.Data.account))
    implementation(project(Dependence.Wallet.Data.settings))
    implementation(project(Dependence.Wallet.Data.events))
    implementation(project(Dependence.Wallet.Data.tonconnect))
    implementation(project(Dependence.Lib.network))
    implementation(project(Dependence.Lib.blockchain))
    implementation(project(Dependence.Lib.security))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.sqlite))
    implementation(project(Dependence.UIKit.core))
}

