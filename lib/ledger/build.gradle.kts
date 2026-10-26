plugins {
    id("target.android.library")
    id("kotlin-parcelize")
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(libs.ton.tvm)
    api(libs.ton.crypto)
    api(libs.ton.tlb)
    api(libs.ton.blockTlb)
    api(libs.ton.tonapiTl)
    api(libs.ton.contract)
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.icu)
    implementation(projects.lib.log)
    implementation(projects.kmp.async)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
}