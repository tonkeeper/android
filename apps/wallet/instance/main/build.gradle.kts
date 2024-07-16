plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
    id("kotlin-kapt")
    id("androidx.baselineprofile")
}

android {
    namespace = Build.namespacePrefix("TonKeeper")
    compileSdk = Build.compileSdkVersion

    defaultConfig {
        applicationId = "com.ton_keeper"
        minSdk = 27 // Build.minSdkVersion
        targetSdk = 34
        versionCode = 600
        versionName = "4.7.2-x"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            /*postprocessing {
                isObfuscate = true
                isOptimizeCode = true
                isRemoveUnusedCode = true
                isRemoveUnusedResources = true
            }*/
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
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
    "baselineProfile"(project(":baselineprofile:main"))
    implementation(project(Dependence.Wallet.app))
}