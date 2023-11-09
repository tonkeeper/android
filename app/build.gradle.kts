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
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        applicationId = "com.tonkeeper"
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    implementation("androidx.camera:camera-camera2:1.4.0-alpha02")
    implementation("androidx.camera:camera-core:1.4.0-alpha02")
    implementation("androidx.camera:camera-lifecycle:1.4.0-alpha02")
    implementation("androidx.camera:camera-view:1.4.0-alpha02")
    implementation("androidx.camera:camera-mlkit-vision:1.4.0-alpha02")

    implementation("com.google.guava:guava:31.0.1-android")
    implementation("com.google.crypto.tink:tink-android:1.10.0")


    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.0")
    implementation("androidx.room:room-ktx:2.6.0")
    implementation("androidx.room:room-runtime:2.6.0")
    annotationProcessor("androidx.room:room-compiler:2.6.0")
    kapt("androidx.room:room-compiler:2.6.0")

    implementation("androidx.biometric:biometric:1.1.0")

    implementation("com.google.android.material:material:1.10.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    implementation("com.airbnb.android:lottie:6.1.0")
    implementation("com.facebook.fresco:fresco:3.1.2")

    implementation("com.squareup.moshi:moshi-kotlin:1.14.0")
    implementation("com.squareup.moshi:moshi-adapters:1.14.0")

    implementation("org.ton:ton-kotlin:0.2.15")

    implementation(platform("com.google.firebase:firebase-bom:32.4.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    implementation(project(":core"))
    implementation(project(":uikit"))
    implementation(project(":ton"))
    implementation(project(":tonapi"))
}