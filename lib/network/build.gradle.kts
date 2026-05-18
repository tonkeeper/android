import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)

    implementation(projects.lib.log)
    implementation(projects.kmp.async)
}