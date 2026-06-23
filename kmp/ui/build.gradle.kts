plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.compose)
    alias(libs.plugins.kotlin.compose)
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    androidLibrary {
        namespace = "com.tonapps.compose.ui"
        compileSdk = libs.versions.android.sdk.compile.get().toInt()
        androidResources.enable = true
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        androidMain.dependencies {
            implementation(projects.ui.uikit.core)
            implementation(libs.bundles.nav3)
        }

        commonMain {
            dependencies {
                implementation(libs.compose.multiplatform.runtime)
                implementation(libs.compose.multiplatform.foundation)
                implementation(libs.compose.multiplatform.ui)
                implementation(libs.compose.multiplatform.ui.util)
                implementation(libs.compose.multiplatform.material3)
                implementation(libs.compose.multiplatform.preview)
                implementation(libs.compose.multiplatform.resources)
                implementation(libs.coil.compose)
                implementation(libs.coil.svg)

                implementation(libs.kotlinx.collections.immutable)
            }
        }
    }
}


dependencies {
    "androidRuntimeClasspath"("org.jetbrains.compose.ui:ui-tooling:1.10.0")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "ui.theme.resources"
    generateResClass = auto
}
