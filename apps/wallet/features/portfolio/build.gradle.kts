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
    implementation(libs.chainkit.models)
    implementation(libs.kotlin.bignum)
    implementation(libs.compose.paging)
    implementation(libs.compose.paging.runtime)

    implementation(projects.apps.wallet.localization)

    implementation(projects.apps.wallet.features.core)
    implementation(projects.apps.wallet.features.embeded.scanner)
    implementation(projects.apps.wallet.features.perps)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.legacy)
    implementation(projects.apps.wallet.data.backup)
    implementation(projects.apps.wallet.data.banner)
    implementation(projects.apps.wallet.data.battery)
    implementation(projects.apps.wallet.data.raffle)
    implementation(projects.apps.wallet.data.features)
    implementation(projects.apps.wallet.data.collectibles)
    implementation(projects.apps.wallet.data.staking)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.multichain.wallet)
    implementation(projects.apps.wallet.data.tx)
    implementation(projects.apps.wallet.api)
    implementation(projects.lib.network)
    implementation(projects.tonapi.wallet)

    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
    implementation(projects.kmp.async)

    implementation(projects.ui.uikit.core)
    implementation(projects.ui.uikit.icon)

    implementation(projects.lib.emoji)
    implementation(projects.lib.icu)
    implementation(projects.lib.extensions)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.log)
    implementation(projects.lib.bus)
}