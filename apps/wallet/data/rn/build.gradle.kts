plugins {
    id("target.android.library")
    id("kotlin-parcelize")
}

dependencies {
    api(platform(libs.firebase.bom))
    api(libs.firebase.crashlytics)

    implementation(libs.androidx.biometric)
    implementation(libs.ton.crypto)
    implementation(libs.koin.core)

    implementation(projects.lib.sqlite)
    implementation(projects.lib.security)
    implementation(projects.lib.extensions)
}


