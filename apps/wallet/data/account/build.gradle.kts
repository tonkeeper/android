plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    kotlin("plugin.serialization") version "1.9.23"
}

android {
    namespace = Build.namespacePrefix("wallet.data.account")
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
    implementation(Dependence.KotlinX.serializationJSON)
    implementation(Dependence.KotlinX.coroutines)
    implementation(Dependence.Koin.core)
    implementation(Dependence.ton)
    implementation(project(Dependence.Module.tonApi))
    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Wallet.Data.rates))
    implementation(project(Dependence.Wallet.api))
    implementation(project(Dependence.Lib.security))
    implementation(project(Dependence.Lib.network))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.blockchain))
    implementation(project(Dependence.Lib.sqlite))

    implementation(project(Dependence.Module.core))
}
