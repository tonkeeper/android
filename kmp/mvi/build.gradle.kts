import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "com.tonapps.kmp.mvi"
        compileSdk = libs.versions.android.sdk.compile.get().toInt()

//        buildFeatures {
//            compose = true
//        }

        withHostTestBuilder {}
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

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xexplicit-backing-fields")
    }
}
