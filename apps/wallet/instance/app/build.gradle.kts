plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
    id("kotlin-kapt")
}

android {
    namespace = Build.namespacePrefix("tonkeeperx")
    compileSdk = Build.compileSdkVersion

    defaultConfig {
        minSdk = Build.minSdkVersion
        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86")
            abiFilters.add("x86_64")
        }
    }

    buildFeatures {
        buildConfig = true
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
    implementation(Dependence.Koin.core)
    implementation(Dependence.Koin.workmanager)
    implementation(Dependence.KotlinX.datetime)
    implementation(Dependence.j2objc)
    implementation(Dependence.cbor)
    implementation(Dependence.TON.tvm)
    implementation(Dependence.TON.crypto)
    implementation(Dependence.TON.tlb)
    implementation(Dependence.TON.blockTlb)
    implementation(Dependence.TON.tonapiTl)
    implementation(Dependence.TON.contract)

    implementation(project(Dependence.Wallet.localization))
    implementation(project(Dependence.Wallet.api))

    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Wallet.Data.tokens))
    implementation(project(Dependence.Wallet.Data.account))
    implementation(project(Dependence.Wallet.Data.settings))
    implementation(project(Dependence.Wallet.Data.rates))
    implementation(project(Dependence.Wallet.Data.collectibles))
    implementation(project(Dependence.Wallet.Data.events))
    implementation(project(Dependence.Wallet.Data.browser))
    implementation(project(Dependence.Wallet.Data.backup))
    implementation(project(Dependence.Wallet.Data.rn))
    implementation(project(Dependence.Wallet.Data.passcode))
    implementation(project(Dependence.Wallet.Data.staking))
    implementation(project(Dependence.Wallet.Data.purchase))
    implementation(project(Dependence.Wallet.Data.battery))
    implementation(project(Dependence.Wallet.Data.dapps))
    implementation(project(Dependence.Wallet.Data.contacts))

    implementation(project(Dependence.UIKit.core))

    implementation(Dependence.AndroidX.core)
    implementation(Dependence.AndroidX.shortcuts)
    implementation(Dependence.AndroidX.appCompat)
    implementation(Dependence.AndroidX.activity)
    implementation(Dependence.AndroidX.fragment)
    implementation(Dependence.AndroidX.recyclerView)
    implementation(Dependence.AndroidX.viewPager2)
    implementation(Dependence.AndroidX.workManager)
    implementation(Dependence.AndroidX.biometric)
    implementation(Dependence.AndroidX.swiperefreshlayout)
    implementation(Dependence.AndroidX.lifecycle)
    implementation(Dependence.AndroidX.webkit)
    implementation(Dependence.AndroidX.browser)

    implementation(Dependence.guava)

    implementation(Dependence.UI.material)
    implementation(Dependence.UI.flexbox)

    implementation(Dependence.Squareup.moshi)
    implementation(Dependence.Squareup.moshiAdapters)

    implementation(platform(Dependence.Firebase.bom))
    implementation(Dependence.Firebase.analytics)
    implementation(Dependence.Firebase.config)
    implementation(Dependence.Firebase.crashlytics)
    implementation(Dependence.Firebase.messaging)
    implementation(Dependence.Firebase.performance)

    implementation(project(Dependence.Module.tonApi))
    implementation(project(Dependence.Module.blur))

    implementation(project(Dependence.Lib.network))
    implementation(project(Dependence.Lib.icu))
    implementation(project(Dependence.Lib.qr))
    implementation(project(Dependence.Lib.emoji))
    implementation(project(Dependence.Lib.security))
    implementation(project(Dependence.Lib.blockchain))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.ledger))
    implementation(project(Dependence.Lib.ur))
    implementation(project(Dependence.Lib.base64))

    implementation(Dependence.AndroidX.Camera.base)
    implementation(Dependence.AndroidX.Camera.core)
    implementation(Dependence.AndroidX.Camera.lifecycle)
    implementation(Dependence.AndroidX.Camera.view)

    implementation(Dependence.GooglePlay.review)
    implementation(Dependence.GooglePlay.billing)
    implementation(Dependence.GooglePlay.update)
    implementation(Dependence.GooglePlay.installreferrer)

    implementation(Dependence.Squareup.okhttp)
    implementation(Dependence.Squareup.sse)
    implementation(Dependence.Analytics.aptabase)

    implementation(Dependence.LedgerHQ.bleManager)

    implementation(Dependence.fresco) {
        exclude(group = "com.facebook.soloader", module = "soloader")
        exclude(group = "com.facebook.fresco", module = "soloader")
        exclude(group = "com.facebook.fresco", module = "nativeimagefilters")
        exclude(group = "com.facebook.fresco", module = "nativeimagetranscoder")
        exclude(group = "com.facebook.fresco", module = "memory-type-native")
        exclude(group = "com.facebook.fresco", module = "imagepipeline-native")
    }
}