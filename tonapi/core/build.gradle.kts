plugins {
    id("target.android.library")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
}
