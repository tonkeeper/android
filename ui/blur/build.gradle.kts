import com.android.build.gradle.internal.dsl.NdkOptions

plugins {
    id("target.android.library")
}

// TODO add doc and script
android {
//    ndkVersion = libs.versions.android.ndk.get()
//
//    defaultConfig {
//        externalNativeBuild {
//            cmake {
//                cppFlags += "-std=c++17 -s"
//            }
//        }
//
//        ndk {
//            debugSymbolLevel = NdkOptions.DebugSymbolLevel.SYMBOL_TABLE.toString()
//        }
//    }
//
//    buildFeatures {
//        prefab = true
//    }
//
//    externalNativeBuild {
//        cmake {
//            path = file("src/main/cpp/CMakeLists.txt")
//        }
//    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(projects.lib.log)
}
