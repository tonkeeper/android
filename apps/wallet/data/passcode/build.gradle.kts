plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.androidx.biometric)
    implementation(libs.koin.core)

    implementation(projects.ui.uikit.core)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.rn)
    implementation(projects.lib.extensions)
    implementation(projects.lib.security)
}
