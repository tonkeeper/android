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
    implementation(libs.kotlin.bignum)
    implementation(libs.chainkit.models)

    implementation(projects.apps.wallet.localization)

    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.dapps)
    implementation(projects.apps.wallet.data.multichain.wallet)
    implementation(projects.apps.wallet.features.core)

    implementation(projects.kmp.core)
    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
    implementation(projects.kmp.async)

    implementation(projects.ui.uikit.icon)
    implementation(projects.ui.uikit.core)

    implementation(projects.lib.wc)
    implementation(projects.lib.icu)
    implementation(projects.lib.extensions)
    implementation(projects.lib.blockchain)
}
