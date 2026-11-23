
plugins {
    id("target.android.app")
}

android {
    namespace = "com.tonapps.signer"
    defaultConfig {
        applicationId = "com.tonapps.signer"
        minSdk = 26
        versionCode = 24
        versionName = "26.11.0"
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    buildFeatures {
        buildConfig = true
        compose = false
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

    implementation(libs.koin.core)
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

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    enabled = true
}

