plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("wallet.data.core")
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
    api(platform(Dependence.Firebase.bom))
    api(Dependence.Firebase.crashlytics)

    implementation(Dependence.TON.tvm)
    implementation(Dependence.TON.crypto)
    implementation(Dependence.TON.tlb)
    implementation(Dependence.TON.blockTlb)
    implementation(Dependence.TON.tonapiTl)
    implementation(Dependence.TON.contract)
    implementation(Dependence.Koin.core)
    implementation(Dependence.AndroidX.biometric)
    implementation(project(Dependence.Wallet.api))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.blockchain))
    implementation(project(Dependence.Lib.sqlite))
    implementation(project(Dependence.Module.tonApi))
    implementation(project(Dependence.UIKit.flag))
}



