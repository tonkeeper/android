plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = Build.namespacePrefix("wallet.http")
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
    api(Dependence.Squareup.okhttp)
    api(Dependence.Squareup.okio)
    api(Dependence.guava)
    implementation(Dependence.Koin.core)
    implementation(Dependence.GooglePlay.cronet)
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.network))
}


