plugins {
    id("target.android.library")
    id("kotlin-parcelize")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.core)
    implementation(libs.ton.tvm)
    implementation(libs.ton.crypto)
    implementation(libs.ton.tlb)
    implementation(libs.ton.blockTlb)
    implementation(libs.ton.tonapiTl)
    implementation(libs.ton.contract)
    implementation(projects.tonapi.legacy)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.rn)
    implementation(projects.apps.wallet.data.rates)
    implementation(projects.apps.wallet.api)
    implementation(projects.lib.security)
    implementation(projects.lib.network)
    implementation(projects.lib.extensions)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.sqlite)
    implementation(projects.lib.ledger)
    implementation(projects.kmp.async)
}
