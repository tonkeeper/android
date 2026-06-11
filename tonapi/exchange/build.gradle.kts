plugins {
    id("target.android.library")
    kotlin("plugin.serialization")
}

dependencies {
    api(projects.tonapi.core)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
}
