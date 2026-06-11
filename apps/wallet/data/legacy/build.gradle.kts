plugins {
    id("target.android.library")
    id("kotlin-parcelize")
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    implementation(libs.ton.tvm)
    implementation(libs.ton.crypto)
    implementation(libs.ton.tlb)
    implementation(libs.ton.blockTlb)
    implementation(libs.ton.tonapiTl)
    implementation(libs.ton.contract)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.icu)
    implementation(projects.tonapi)

    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.purchase)
    implementation(projects.apps.wallet.data.rates)
    implementation(projects.apps.wallet.data.tokens)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.staking)
}
