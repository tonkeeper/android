@file:Suppress("UnstableApiUsage")

import com.android.SdkConstants.ABI_ARM64_V8A
import com.android.SdkConstants.ABI_ARMEABI_V7A
import com.android.SdkConstants.ABI_INTEL_ATOM
import com.android.SdkConstants.ABI_INTEL_ATOM64
import com.android.build.gradle.internal.dsl.NdkOptions

plugins {
    id("target.android.library")
    id("kotlin-parcelize")
}

// TODO add script and doc
//android {
//    ndkVersion = libs.versions.android.ndk.get()
//
//    defaultConfig {
//        ndk {
//            debugSymbolLevel = NdkOptions.DebugSymbolLevel.SYMBOL_TABLE.toString()
//            abiFilters += setOf(ABI_INTEL_ATOM, ABI_INTEL_ATOM64, ABI_ARMEABI_V7A, ABI_ARM64_V8A)
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
//}

dependencies {
    implementation(libs.chainkit.mnemonic)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.security)
    implementation(projects.lib.extensions)
    implementation(projects.lib.bus)
    compileOnly(fileTree("libs") {
        include("*.aar")
    })

}
