plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.chainkit.sdk)
    implementation(libs.chainkit.swap)
    implementation(libs.kotlin.bignum)

    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.multichain.wallet)
    implementation(projects.apps.wallet.data.features)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.tonapi.exchange)
    implementation(projects.lib.extensions)
    implementation(projects.lib.features)
    implementation(projects.kmp.async)
}
