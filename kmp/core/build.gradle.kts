plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    id("com.android.kotlin.multiplatform.library")
}

version = "1.0"

kotlin {
    androidLibrary {
        namespace = "com.tonapps.compose.core"
        compileSdk = libs.versions.android.sdk.compile.get().toInt()
    }

    applyDefaultHierarchyTemplate()
}
