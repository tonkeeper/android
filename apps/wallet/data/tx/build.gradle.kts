plugins {
    id("target.android.library")
    id("kotlin-parcelize")
}

dependencies {
    implementation(libs.koin.core)

    implementation(projects.lib.blockchain)
    implementation(projects.lib.network)
    implementation(projects.tonapi.legacy)

    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.rates)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.battery)
    implementation(projects.apps.wallet.data.tokens)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.features)
    implementation(projects.lib.features)

    implementation(projects.kmp.async)
    implementation(projects.lib.extensions)
    implementation(projects.lib.icu)
}
