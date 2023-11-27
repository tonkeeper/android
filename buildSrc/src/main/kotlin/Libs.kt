object Libs {

    const val ton = "org.ton:ton-kotlin:0.2.15"
    const val lottie = "com.airbnb.android:lottie:6.1.0"
    const val fresco = "com.facebook.fresco:fresco:3.1.2"
    const val guava = "com.google.guava:guava:31.0.1-android"
    const val zxing = "com.google.zxing:core:3.5.2"
    const val mlKitBarcode = "com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.0"
    const val sodium = "com.github.joshjdevl.libsodiumjni:libsodium-jni-aar:2.0.1"


    object KotlinX {
        const val coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1"
        const val serialization = "org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.0"
    }

    object UI {
        const val material = "com.google.android.material:material:1.10.0"
    }

    object AndroidX {
        const val core = "androidx.core:core-ktx:1.12.0"
        const val appCompat = "androidx.appcompat:appcompat:1.6.1"
        const val activity = "androidx.activity:activity-ktx:1.8.1"
        const val fragment = "androidx.fragment:fragment-ktx:1.6.2"
        const val recyclerView = "androidx.recyclerview:recyclerview:1.3.2"
        const val viewPager2 = "androidx.viewpager2:viewpager2:1.0.0"
        const val security = "androidx.security:security-crypto:1.0.0"
        const val workManager = "androidx.work:work-runtime-ktx:2.8.1"
        const val biometric = "androidx.biometric:biometric:1.1.0"
        const val annotation = "androidx.annotation:annotation:1.7.0"
        const val splashscreen = "androidx.core:core-splashscreen:1.0.0"

        object Camera {
            private const val version = "1.4.0-alpha02"

            const val base = "androidx.camera:camera-camera2:$version"
            const val core = "androidx.camera:camera-core:$version"
            const val lifecycle = "androidx.camera:camera-lifecycle:$version"
            const val view = "androidx.camera:camera-view:$version"
            const val vision = "androidx.camera:camera-mlkit-vision:$version"
        }

        object Room {
            private const val version = "2.6.0"

            const val base = "androidx.room:room-ktx:$version"
            const val runtime = "androidx.room:room-runtime:$version"
            const val compiler = "androidx.room:room-compiler:$version"
        }
    }

    object Squareup {
        const val okhttp = "com.squareup.okhttp3:okhttp:4.12.0"
        const val sse = "com.squareup.okhttp3:okhttp-sse:4.12.0"
        const val moshi = "com.squareup.moshi:moshi-kotlin:1.15.0"
        const val moshiAdapters = "com.squareup.moshi:moshi-adapters:1.15.0"
    }

    object Firebase {
        const val bom = "com.google.firebase:firebase-bom:32.4.0"
        const val analytics = "com.google.firebase:firebase-analytics-ktx"
        const val crashlytics = "com.google.firebase:firebase-crashlytics-ktx"
    }

    object Module {
        const val core = ":core"
        const val uiKit = ":uikit"
        const val ton = ":ton"
        const val tonApi = ":tonapi"
        const val shimmer = ":shimmer"
    }
}