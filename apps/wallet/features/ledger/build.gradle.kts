plugins {
    id("target.android.compose")
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

    implementation(projects.apps.wallet.localization)
    implementation(projects.apps.wallet.features.core)
    implementation(projects.apps.wallet.data.ledger)
    implementation(projects.apps.wallet.data.multichain.wallet)
    implementation(projects.apps.wallet.data.settings)

    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
    implementation(projects.kmp.async)

    implementation(projects.ui.uikit.core)
    implementation(projects.ui.uikit.icon)
    implementation(projects.ui.uikit.color)

    implementation(projects.lib.bus)
    implementation(projects.lib.ledgerkit)
    implementation(projects.lib.log)
    implementation(projects.lib.extensions)

    testImplementation(libs.junit)
    testImplementation(testFixtures(projects.apps.wallet.testing))
}

tasks.withType<Test>().configureEach {
    enabled = true
}
