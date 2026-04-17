plugins {
    id("target.android.library")
}

dependencies {
    implementation(projects.lib.extensions)
    implementation(libs.kotlinx.coroutines.core)

    // Analytic aptabase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.aptabase)
    implementation(libs.kotlinx.coroutines.core)
    implementation(projects.kmp.async)
    implementation(projects.lib.blockchain)
}
