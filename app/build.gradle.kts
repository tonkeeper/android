plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("kotlinx-serialization")
    id("kotlin-kapt")
}

android {
    namespace = "com.tonkeeper"
    compileSdk = Build.compileSdkVersion

    defaultConfig {
        applicationId = "com.tonkeeper"
        minSdk = Build.minSdkVersion
        targetSdk = 34
        versionCode = 5
        versionName = "0.0.4-beta"
    }

    buildFeatures {
        buildConfig = true
    }

    /*signingConfigs {
        getByName("release") {
            storeFile = file("release.keystore")
            storePassword = "KonG6429Z1SF"
            keyAlias = "tonkeeper"
            keyPassword = "KonG6429Z1SF"
        }
    }*/

    buildTypes {
        release {
            // signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation(Libs.AndroidX.core)
    implementation(Libs.AndroidX.appCompat)
    implementation(Libs.AndroidX.activity)
    implementation(Libs.AndroidX.fragment)
    implementation(Libs.AndroidX.recyclerView)
    implementation(Libs.AndroidX.viewPager2)
    implementation(Libs.AndroidX.security)
    implementation(Libs.AndroidX.workManager)
    implementation(Libs.AndroidX.biometric)

    implementation(Libs.AndroidX.Camera.base)
    implementation(Libs.AndroidX.Camera.core)
    implementation(Libs.AndroidX.Camera.lifecycle)
    implementation(Libs.AndroidX.Camera.view)
    implementation(Libs.AndroidX.Camera.vision)


    implementation(Libs.mlKitBarcode)

    implementation(Libs.guava)
    implementation(Libs.sodium)


    implementation(Libs.KotlinX.serialization)
    implementation(Libs.AndroidX.Room.base)
    implementation(Libs.AndroidX.Room.runtime)
    annotationProcessor(Libs.AndroidX.Room.compiler)
    kapt(Libs.AndroidX.Room.compiler)



    implementation(Libs.UI.material)
    implementation(Libs.zxing)

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

    implementation(project(Libs.Module.core))
    implementation(project(Libs.Module.uiKit))
    implementation(project(Libs.Module.ton))
    implementation(project(Libs.Module.tonApi))
    implementation(project(Libs.Module.shimmer))
}