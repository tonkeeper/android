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
    namespace = Build.namespacePrefix("tonkeeperx")
    compileSdk = Build.compileSdkVersion

    defaultConfig {
        applicationId = "com.tonapps.tonkeeperx"
        minSdk = Build.minSdkVersion
        targetSdk = 34
        versionCode = 26
        versionName = "0.0.25"
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
        }

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
    "baselineProfile"(project(":baselineprofile:x"))
    implementation(project(Libs.Wallet.localization))
    implementation(project(Libs.Wallet.Data.core))
    implementation(project(Libs.Wallet.Data.account))
    implementation(project(Libs.Wallet.Data.settings))
    implementation(project(Libs.UIKit.core))

    implementation(Libs.AndroidX.core)
    implementation(Libs.AndroidX.appCompat)
    implementation(Libs.AndroidX.activity)
    implementation(Libs.AndroidX.fragment)
    implementation(Libs.AndroidX.recyclerView)
    implementation(Libs.AndroidX.viewPager2)
    implementation(Libs.AndroidX.security)
    implementation(Libs.AndroidX.workManager)
    implementation(Libs.AndroidX.biometric)
    implementation(Libs.AndroidX.swiperefreshlayout)

    implementation(Libs.AndroidX.Camera.base)
    implementation(Libs.AndroidX.Camera.core)
    implementation(Libs.AndroidX.Camera.lifecycle)
    implementation(Libs.AndroidX.Camera.view)
    implementation(Libs.ML.barcode)

    implementation(Libs.guava)
    implementation(Libs.sodium)


    implementation(Libs.KotlinX.serialization)
    implementation(Libs.KotlinX.datetime)
    implementation(Libs.AndroidX.Room.base)
    implementation(Libs.AndroidX.Room.runtime)
    annotationProcessor(Libs.AndroidX.Room.compiler)
    kapt(Libs.AndroidX.Room.compiler)



    implementation(Libs.UI.material)

    implementation(Libs.Squareup.okhttp)
    implementation(Libs.Squareup.sse)
    implementation(Libs.Squareup.moshi)
    implementation(Libs.Squareup.moshiAdapters)

    implementation(Libs.lottie)
    implementation(Libs.fresco)


    implementation(Libs.ton)

    implementation(platform(Libs.Firebase.bom))
    implementation(Libs.Firebase.analytics)
    implementation(Libs.Firebase.crashlytics)
    implementation(Libs.Firebase.messaging)

    implementation(project(Libs.Module.core))
    implementation(project(Libs.Module.ton))
    implementation(project(Libs.Module.tonApi))
    implementation(project(Libs.Module.shimmer))
    implementation(project(Libs.Module.blur))

    implementation(project(Libs.Lib.network))
    implementation(project(Libs.Lib.qr))
    implementation(project(Libs.Lib.emoji))

    implementation(Libs.Koin.core)
}