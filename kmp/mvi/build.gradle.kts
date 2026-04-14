import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidLibrary {
        namespace = "com.tonapps.kmp.mvi"
        compileSdk = libs.versions.android.sdk.compile.get().toInt()

//        buildFeatures {
//            compose = true
//        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.kmp.async)
            implementation(projects.lib.log)

            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.viewmodel)
            implementation(libs.androidx.annotation)
        }
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
