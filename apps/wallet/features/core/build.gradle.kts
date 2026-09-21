plugins {
    id("target.android.compose")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.chainkit.models)
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.debugTooling)

    implementation(platform(libs.firebase.bom))

    implementation(libs.bundles.nav3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.core)
    api(libs.compose.paging)
    api(libs.compose.paging.runtime)

    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.collectibles)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.rn)
    implementation(projects.apps.wallet.data.multichain.wallet) // TODO extract models and remove this
    implementation(projects.apps.wallet.data.multichain.exchange)

    implementation(projects.apps.wallet.localization)

    implementation(projects.kmp.core)
    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
    implementation(projects.kmp.async)

    implementation(projects.ui.uikit.icon)
    implementation(projects.ui.uikit.core)

    implementation(projects.lib.icu)
    implementation(projects.lib.bus)
    implementation(projects.lib.emoji)
    implementation(projects.lib.extensions)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.features)
    implementation(projects.apps.wallet.data.features)

    testImplementation(libs.junit)
}
