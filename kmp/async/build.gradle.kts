import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    androidLibrary {
        namespace = "com.tonapps.kmp.async"
        compileSdk = libs.versions.android.sdk.compile.get().toInt()
    }

    applyDefaultHierarchyTemplate()
}


kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.lib.log)
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xexplicit-backing-fields")
    }
}
