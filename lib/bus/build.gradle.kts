plugins {
    id("target.android.library")
}

dependencies {
    implementation(projects.lib.extensions)
    implementation(projects.kmp.async)

    // Analytic aptabase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.aptabase)
    implementation(libs.kotlinx.coroutines.core)
}
