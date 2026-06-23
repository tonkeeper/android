plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.kotlinx.collections.immutable)

    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.events)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.localization)
    implementation(projects.apps.wallet.features.core)

    implementation(projects.kmp.ui)
    implementation(projects.ui.uikit.icon)

    implementation(projects.lib.blockchain)
    implementation(projects.lib.extensions)
    implementation(projects.lib.icu)
}
