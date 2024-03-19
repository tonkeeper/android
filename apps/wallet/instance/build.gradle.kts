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
        versionCode = 31
        versionName = "0.0.29"
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

    implementation(Dependence.Koin.core)

    implementation(project(Dependence.Wallet.localization))
    implementation(project(Dependence.Wallet.api))

    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Wallet.Data.tokens))
    implementation(project(Dependence.Wallet.Data.account))
    implementation(project(Dependence.Wallet.Data.settings))
    implementation(project(Dependence.Wallet.Data.rates))
    implementation(project(Dependence.Wallet.Data.collectibles))
    implementation(project(Dependence.Wallet.Data.events))

    implementation(project(Dependence.UIKit.core))

    implementation(Dependence.AndroidX.core)
    implementation(Dependence.AndroidX.appCompat)
    implementation(Dependence.AndroidX.activity)
    implementation(Dependence.AndroidX.fragment)
    implementation(Dependence.AndroidX.recyclerView)
    implementation(Dependence.AndroidX.viewPager2)
    implementation(Dependence.AndroidX.workManager)
    implementation(Dependence.AndroidX.biometric)
    implementation(Dependence.AndroidX.swiperefreshlayout)

    implementation(Dependence.guava)
    implementation(Dependence.sodium)

    /*implementation(Dependence.KotlinX.serialization)
    implementation(Dependence.KotlinX.datetime)
    implementation(Dependence.AndroidX.Room.base)
    implementation(Dependence.AndroidX.Room.runtime)
    annotationProcessor(Dependence.AndroidX.Room.compiler)
    kapt(Dependence.AndroidX.Room.compiler)*/


    implementation(Dependence.UI.material)

    implementation(Dependence.Squareup.moshi)
    implementation(Dependence.Squareup.moshiAdapters)

    implementation(platform(Dependence.Firebase.bom))
    implementation(Dependence.Firebase.analytics)
    implementation(Dependence.Firebase.crashlytics)
    implementation(Dependence.Firebase.messaging)

    implementation(project(Dependence.Module.core))
    implementation(project(Dependence.Module.tonApi))
    implementation(project(Dependence.Module.blur))

    implementation(project(Dependence.Lib.network))
    implementation(project(Dependence.Lib.icu))
    implementation(project(Dependence.Lib.qr))
    implementation(project(Dependence.Lib.emoji))
    implementation(project(Dependence.Lib.blockchain))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Module.ton))

    implementation(Dependence.AndroidX.Camera.base)
    implementation(Dependence.AndroidX.Camera.core)
    implementation(Dependence.AndroidX.Camera.lifecycle)
    implementation(Dependence.AndroidX.Camera.view)

    implementation(Dependence.Squareup.okhttp)
    implementation(Dependence.Squareup.sse)

    implementation(Dependence.fresco)


    implementation(Dependence.AndroidX.Room.base)
    implementation(Dependence.AndroidX.Room.runtime)
    annotationProcessor(Dependence.AndroidX.Room.compiler)
    kapt(Dependence.AndroidX.Room.compiler)


}