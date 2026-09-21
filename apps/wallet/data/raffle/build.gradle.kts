plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.koin.core)

    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.features)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.tonapi.wallet)

    implementation(projects.lib.extensions)
    implementation(projects.lib.features)
    implementation(projects.lib.log)
}
