import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing)

    api(libs.barcode.scanning)
    implementation(libs.camerax.base)

    implementation(projects.lib.log)
    implementation(projects.kmp.async)
}