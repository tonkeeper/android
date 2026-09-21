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
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.compose.paging)
    implementation(libs.compose.paging.runtime)

    implementation(projects.apps.wallet.api)
    implementation(projects.tonapi.perps)
    implementation(projects.tonapi.kandelabr)
    implementation(projects.tonapi.trading)

    implementation(projects.apps.wallet.features.core)
    implementation(projects.apps.wallet.localization)
    implementation(projects.apps.wallet.data.multichain.wallet)

    implementation(projects.kmp.core)
    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
    implementation(projects.kmp.async)
    implementation(projects.kmp.chart)

    implementation(projects.ui.uikit.icon)
    implementation(projects.ui.uikit.core)

    implementation(projects.lib.blockchain)
    implementation(projects.lib.extensions)
    implementation(projects.lib.log)
}
