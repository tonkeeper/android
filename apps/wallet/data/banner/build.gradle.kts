plugins {
    id("target.android.library")
    id("kotlin-parcelize")
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.koin.core)

    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.settings)

    implementation(projects.lib.blockchain)
    implementation(projects.lib.network)
    implementation(projects.lib.extensions)
}
