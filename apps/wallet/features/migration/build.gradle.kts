plugins {
    id("target.android.compose")
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.debugTooling)

    implementation(libs.bundles.nav3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(projects.apps.wallet.localization)
    implementation(projects.apps.wallet.api)
    implementation(projects.tonapi.tonkeeper)
    implementation(projects.tonapi.battery)
    implementation(projects.tonapi.wallet)

    implementation(projects.apps.wallet.features.core)
    implementation(projects.apps.wallet.features.onboarding)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.backup)
    implementation(projects.apps.wallet.data.multichain.wallet)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.rates)
    implementation(projects.apps.wallet.data.events)
    implementation(projects.apps.wallet.data.tx)
    implementation(projects.apps.wallet.data.tokens)
    implementation(projects.apps.wallet.data.battery)
    implementation(projects.apps.wallet.data.features)

    implementation(projects.apps.wallet.features.ramp)

    implementation(projects.apps.wallet.features.events)

    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
    implementation(projects.kmp.async)

    implementation(projects.ui.uikit.core)

    implementation(projects.lib.blockchain)
    implementation(projects.lib.bus)
    implementation(projects.lib.extensions)
    implementation(projects.lib.icu)
    implementation(projects.lib.log)
}
