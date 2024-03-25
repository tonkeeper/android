plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "core"
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
    implementation(Dependence.AndroidX.lifecycle)
    implementation(Dependence.AndroidX.security)

    implementation(Dependence.Squareup.okhttp)
    implementation(Dependence.Squareup.sse)
    implementation(Dependence.zxing)

    implementation(Dependence.ML.barcode)
    implementation(Dependence.AndroidX.Camera.base)
    implementation(Dependence.ton)
}