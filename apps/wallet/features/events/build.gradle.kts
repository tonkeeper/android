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

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.chainkit.models)
    implementation(libs.compose.paging)
    implementation(libs.compose.paging.runtime)

    implementation(libs.kotlinx.collections.immutable)

    implementation(projects.apps.wallet.localization)

    implementation(projects.apps.wallet.features.core)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.events)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.multichain.wallet)
    implementation(projects.apps.wallet.data.staking)
    implementation(projects.apps.wallet.api)
    implementation(projects.tonapi.wallet)
    implementation(projects.tonapi.tonkeeper)

    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
    implementation(projects.kmp.async)

    implementation(projects.ui.uikit.icon)

    implementation(projects.lib.extensions)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.icu)
    implementation(projects.lib.network)

    implementation(projects.ui.uikit.core)
}
