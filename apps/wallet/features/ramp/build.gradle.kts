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
    implementation(libs.coil.compose) // TODO remove, use from DesSys

    implementation(projects.tonapi.legacy)

    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.localization)

    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.tokens)
    implementation(projects.apps.wallet.data.rates)
    implementation(projects.apps.wallet.data.battery)
    implementation(projects.apps.wallet.data.contacts)
    implementation(projects.apps.wallet.data.events)
    implementation(projects.apps.wallet.data.rn)
    implementation(projects.apps.wallet.data.passcode)
    implementation(projects.apps.wallet.data.collectibles)

    implementation(projects.apps.wallet.data.tx)

    implementation(projects.tonapi.exchange)
    implementation(projects.tonapi.battery)
    implementation(projects.apps.wallet.data.legacy)

    implementation(projects.apps.wallet.features.core)
    implementation(projects.apps.wallet.features.dapp)
    implementation(projects.apps.wallet.features.embeded.scanner)
    implementation(projects.apps.wallet.data.dapps)

    implementation(projects.kmp.core)
    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
    implementation(projects.kmp.async)

    implementation(projects.ui.uikit.icon)
    implementation(projects.ui.uikit.core)

    implementation(projects.lib.qr)
    implementation(projects.lib.bus)
    implementation(projects.lib.icu)
    implementation(projects.lib.ledger)
    implementation(projects.lib.extensions)
    implementation(projects.lib.blockchain)
}
