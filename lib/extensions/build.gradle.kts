plugins {
    id("target.android.library")
    id("kotlin-parcelize")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.core)
    implementation(libs.androidx.core)
    implementation(libs.androidx.security)
    implementation(projects.ui.uikit.core)
    implementation(projects.lib.icu)
    implementation(projects.lib.base64)
    implementation(libs.google.play.installreferrer)
    implementation(libs.google.play.base)
    api(projects.lib.log)
}
