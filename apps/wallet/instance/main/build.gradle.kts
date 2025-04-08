@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
    id("kotlin-kapt")
    id("androidx.baselineprofile")
    id("com.google.firebase.firebase-perf")
}

val isCI = project.hasProperty("android.injected.signing.store.file")
var isAPK = gradle.startParameter.projectProperties["isApk"]?.toBoolean() ?: false

android {
    namespace = Build.namespacePrefix("TonKeeper")
    compileSdk = Build.compileSdkVersion

    defaultConfig {
        applicationId = "com.ton_keeper"
        minSdk = Build.minSdkVersion
        targetSdk = 35
        versionCode = 600

        versionName = "5.1.2" // Format is "major.minor.patch" (e.g. "1.0.0") and only numbers are allowed

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "version"

    productFlavors {
        create("default") { }
        create("site") { }
        create("uk") {
            applicationIdSuffix = ".uk"
        }
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
            if (isCI) {
                signingConfig = signingConfigs.getByName("release")
                manifestPlaceholders += if (isAPK) {
                    mapOf("build_type" to "site")
                } else {
                    mapOf("build_type" to "google_play")
                }
            } else {
                manifestPlaceholders += mapOf("build_type" to "manual")
            }
        }

        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders += mapOf("build_type" to "internal_debug")
        }
    }

    experimentalProperties["android.experimental.art-profile-r8-rewriting"] = true
    experimentalProperties["android.experimental.r8.dex-startup-optimization"] = true

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

baselineProfile {
    saveInSrc = true
    dexLayoutOptimization = true
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(project(Dependence.Wallet.app))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    // implementation(Dependence.AndroidX.profileinstaller)
    baselineProfile(project(":baselineprofile:main"))

    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
