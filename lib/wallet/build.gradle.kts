plugins {
    id("target.android.library")
}

dependencies {
    implementation(projects.lib.extensions)
    implementation(projects.lib.log)
    implementation(libs.chainkit.sdk)
    implementation(libs.chainkit.chain.ethereum)
    implementation(libs.chainkit.chain.ton)
    implementation(libs.chainkit.net.json)
    implementation(libs.koin.core)
    implementation(libs.walletcore)
}
