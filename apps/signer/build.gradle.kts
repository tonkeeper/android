plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tonapps.signer"
    compileSdk = Build.compileSdkVersion

    defaultConfig {
        applicationId = "com.tonapps.signer"
        minSdk = Build.minSdkVersion
        targetSdk = 34
        versionCode = 4
        versionName = "0.0.4"
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
    implementation(Libs.AndroidX.core)
    implementation(Libs.AndroidX.appCompat)
    implementation(Libs.AndroidX.activity)
    implementation(Libs.AndroidX.fragment)
    implementation(Libs.AndroidX.recyclerView)
    implementation(Libs.AndroidX.viewPager2)

    implementation(Libs.UI.material)
    implementation(Libs.AndroidX.Camera.base)
    implementation(Libs.AndroidX.Camera.core)
    implementation(Libs.AndroidX.Camera.lifecycle)
    implementation(Libs.AndroidX.Camera.view)
    implementation(Libs.AndroidX.security)

    implementation(Libs.ton)

    implementation(project(Libs.Module.uiKit)) {
        exclude("com.airbnb.android", "lottie")
        // exclude("com.facebook.fresco", "fresco")
    }

    implementation(project(Libs.Module.qr))

    implementation(project(Libs.Module.security))
    
    implementation(Libs.Koin.core)
}

