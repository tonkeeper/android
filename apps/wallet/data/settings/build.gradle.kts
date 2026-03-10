plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.koin.core)

    implementation(projects.lib.extensions)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.rn)
    implementation(projects.apps.wallet.localization)
}
