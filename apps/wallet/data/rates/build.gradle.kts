plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("wallet.data.rates")
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
    implementation(project(Dependence.Wallet.api))
    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Module.tonApi))
    implementation(project(Dependence.Lib.blockchain))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.icu))
}

