plugins {
    id("target.android.library")
    kotlin("plugin.serialization")
}

android {
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.nordic.ble)
    implementation(libs.nordic.ble.ktx)
    implementation(libs.androidx.core)
    implementation(libs.web3j)
    implementation(libs.bcprovjdk)
    implementation(projects.lib.log)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    enabled = true
}
