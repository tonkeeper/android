plugins {
    id("target.android.library")
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(platform(libs.firebase.bom))
    api(libs.firebase.crashlytics)

    implementation(libs.kotlinx.serialization.core)
    implementation(libs.ton.tvm)
    implementation(libs.ton.crypto)
    implementation(libs.ton.tlb)
    implementation(libs.ton.blockTlb)
    implementation(libs.ton.tonapiTl)
    implementation(libs.ton.contract)
    implementation(libs.koin.core)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite.bundled)

    implementation(projects.apps.wallet.api)
    implementation(projects.lib.extensions)
    implementation(projects.lib.blockchain)
    implementation(projects.kmp.async)
    implementation(projects.lib.sqlite)
    implementation(projects.lib.icu)
    implementation(projects.tonapi.legacy)
    implementation(projects.ui.uikit.flag)
}
