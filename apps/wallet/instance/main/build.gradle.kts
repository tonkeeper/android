@file:Suppress("UnstableApiUsage")
import com.android.SdkConstants.ABI_ARM64_V8A
import com.android.SdkConstants.ABI_ARMEABI_V7A
import com.android.SdkConstants.ABI_INTEL_ATOM
import com.android.SdkConstants.ABI_INTEL_ATOM64

plugins {
    id("target.android.app")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
    id("androidx.baselineprofile")
    id("com.google.firebase.firebase-perf")
    alias(libs.plugins.kotlin.compose)
}

val isCI = project.hasProperty("android.injected.signing.store.file")
var isAPK = gradle.startParameter.projectProperties["isApk"]?.toBoolean() ?: false

android {
    namespace = "com.ton_keeper"
    defaultConfig {
        applicationId = "com.ton_keeper"
        versionCode = System.getenv("VERSION_CODE")?.toInt() ?: 1000000000 // more than github
        versionName = System.getenv("VERSION_NAME") ?: "26.03.0" // Format is "yy.mm.iteration" (e.g. "26.02.0") and only numbers are allowed
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "version"

    productFlavors {
        create("default") {
            dimension = "version"

        }
        create("site") {
            dimension = "version"
            matchingFallbacks += listOf("default")
        }
        create("uk") {
            dimension = "version"
            applicationIdSuffix = ".uk"
            matchingFallbacks += listOf("default")
        }
    }

    lint {
        disable += "Instantiatable"
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true

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

            ndk { abiFilters += setOf(ABI_INTEL_ATOM, ABI_INTEL_ATOM64, ABI_ARMEABI_V7A, ABI_ARM64_V8A) }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        create("beta") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
            
            resValue("string", "app_name", "Tonkeeper Beta")
            applicationIdSuffix = ".beta"
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders += mapOf("build_type" to "internal_beta")
            matchingFallbacks += listOf("debug")

            ndk { abiFilters += setOf(ABI_INTEL_ATOM64, ABI_ARM64_V8A) }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        debug {
            isMinifyEnabled = false

            resValue("string", "app_name", "Tonkeeper Dev")
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders += mapOf("build_type" to "internal_debug")

            ndk { abiFilters += setOf(ABI_ARM64_V8A) }
        }
    }

    experimentalProperties["android.experimental.art-profile-r8-rewriting"] = true
    experimentalProperties["android.experimental.r8.dex-startup-optimization"] = true

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

baselineProfile {
    saveInSrc = true
    dexLayoutOptimization = true
    mergeIntoMain = true
    baselineProfileRulesRewrite = true
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.koin.core)
    implementation(libs.koin.workmanager)
    implementation(libs.coil.compose)
    implementation(libs.camerax.core)
    implementation(libs.camerax.base)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.test.uiautomator)

    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile:main"))

//    debugImplementation(libs.leakcanary)

    implementation(projects.apps.wallet.features.core)
    implementation(projects.apps.wallet.features.trading)
    implementation(projects.apps.wallet.features.ramp)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.passcode)
    implementation(projects.apps.wallet.data.staking)
    implementation(projects.apps.wallet.data.purchase)
    implementation(projects.apps.wallet.data.battery)
    implementation(projects.apps.wallet.data.dapps)
    implementation(projects.apps.wallet.data.contacts)
    implementation(projects.apps.wallet.data.swap)
    implementation(projects.apps.wallet.data.plugins)
    implementation(projects.apps.wallet.data.features)
    implementation(projects.apps.wallet.data.tokens)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.rates)
    implementation(projects.apps.wallet.data.collectibles)
    implementation(projects.apps.wallet.data.events)
    implementation(projects.apps.wallet.data.browser)
    implementation(projects.apps.wallet.data.backup)
    implementation(projects.apps.wallet.data.rn)
    implementation(projects.apps.wallet.localization)
    implementation(projects.apps.wallet.api)

    implementation(projects.lib.features)
    implementation(projects.lib.icu)
    implementation(projects.lib.extensions)
    implementation(projects.lib.log)

    implementation(projects.kmp.async)
    implementation(projects.kmp.mvi)

    implementation(projects.apps.wallet.localization)
    implementation(projects.apps.wallet.instance.app)
}
