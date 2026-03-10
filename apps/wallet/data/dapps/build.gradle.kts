plugins {
    id("target.android.library")
    id("kotlin-parcelize")
}

dependencies {
    implementation(libs.ton.tvm)
    implementation(libs.ton.crypto)
    implementation(libs.ton.tlb)
    implementation(libs.ton.blockTlb)
    implementation(libs.ton.tonapiTl)
    implementation(libs.ton.contract)
    implementation(libs.koin.core)

    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.rn)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.extensions)
    implementation(projects.lib.sqlite)
    implementation(projects.lib.security)
    implementation(projects.lib.network)
    implementation(projects.lib.base64)
}