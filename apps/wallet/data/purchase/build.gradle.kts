plugins {
    id("target.android.library")
    id("kotlin-parcelize")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.api)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.extensions)
    implementation(projects.tonapi.legacy)

    api(libs.ton.tvm)
    api(libs.ton.crypto)
    api(libs.ton.tlb)
    api(libs.ton.blockTlb)
    api(libs.ton.tonapiTl)
    api(libs.ton.contract)
    implementation(libs.koin.core)
}
