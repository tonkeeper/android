plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("ledger")
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
    api(Dependence.TON.tvm)
    api(Dependence.TON.crypto)
    api(Dependence.TON.tlb)
    api(Dependence.TON.blockTlb)
    api(Dependence.TON.tonapiTl)
    api(Dependence.TON.contract)
    implementation(Dependence.AndroidX.core)
    implementation(Dependence.KotlinX.coroutines)
    implementation(Dependence.LedgerHQ.bleManager)
    implementation(project(Dependence.Lib.blockchain))
}