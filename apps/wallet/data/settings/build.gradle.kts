plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.koin.core)

    implementation(projects.lib.extensions)
    implementation(projects.lib.blockchain)
    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.rn)
    implementation(projects.apps.wallet.localization)
}
