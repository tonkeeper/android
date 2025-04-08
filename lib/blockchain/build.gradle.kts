plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("blockchain")
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

    api(Dependence.TON.tvm)
    api(Dependence.TON.crypto)
    api(Dependence.TON.tlb)
    api(Dependence.TON.blockTlb)
    api(Dependence.TON.tonapiTl)
    api(Dependence.TON.contract)
    api(Dependence.KotlinX.io)
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.base64))
}