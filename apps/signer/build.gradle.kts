
plugins {
    id("target.android.app")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tonapps.signer"
    defaultConfig {
        applicationId = "com.tonapps.signer"
        minSdk = 26
        versionCode = 23
        versionName = "0.2.3"
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "ENVIRONMENT", "\"\"")
        }

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "ENVIRONMENT", "\"dev\"")
        }
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.recyclerView)
    implementation(libs.androidx.viewPager2)
    implementation(libs.androidx.splashscreen)

    implementation(libs.material)
    implementation(libs.flexbox)
    implementation(libs.camerax.base)
    implementation(libs.camerax.core)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.androidx.security)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycleSavedState)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.extensions)

    implementation(libs.kotlinx.coroutines.core)

    implementation(projects.ui.uikit.core) {
        exclude("com.airbnb.android", "lottie")
        exclude("com.facebook.fresco", "fresco")
    }

    implementation(projects.lib.qr)
    implementation(projects.lib.security)
    implementation(projects.lib.icu)
    implementation(libs.koin.core)
}

