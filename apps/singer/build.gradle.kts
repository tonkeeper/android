plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tonapps.singer"
    compileSdk = Build.compileSdkVersion

    defaultConfig {
        applicationId = "com.tonapps.singer"
        minSdk = Build.minSdkVersion
        targetSdk = 34
        versionCode = 1
        versionName = "0.0.1"
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
    implementation(Libs.AndroidX.swiperefreshlayout)

    implementation(Libs.UI.material)
    implementation(Libs.zxing)

    implementation(Libs.ton)

    implementation(project(Libs.Module.core))
    implementation(project(Libs.Module.uiKit))
    implementation(project(Libs.Module.ton))
    implementation(project(Libs.Module.shimmer))
    implementation(project(Libs.Module.blur))

    implementation(Libs.Koin.core)
    implementation(project(Libs.argon2kt))
}

