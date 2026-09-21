plugins {
    id("target.android.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.lib.extensions)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.wallet)
    implementation(projects.kmp.async)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.chainkit.sdk)
    implementation(libs.koin.core)
    implementation("com.trustwallet:wallet-core-kotlin:4.6.0")

    implementation("com.reown:android-core:1.6.12")
    implementation("com.reown:walletkit:1.6.12")
}
