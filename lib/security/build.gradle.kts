@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.dsl.NdkOptions

plugins {
    id("target.android.library")
    id("kotlin-parcelize")
}

// TODO add script and doc
android {
//    ndkVersion = libs.versions.android.ndk.get()
//
//    defaultConfig {
//        ndk {
//            debugSymbolLevel = NdkOptions.DebugSymbolLevel.SYMBOL_TABLE.toString()
//        }
//    }
//
//    externalNativeBuild {
//        cmake {
//            path = file("src/main/cpp/CMakeLists.txt")
//
//        }
//    }
//
//    buildFeatures {
//        prefab = true
//    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.security)
    implementation(projects.lib.extensions)
    compileOnly(fileTree("libs") {
        include("*.aar")
    })

}
