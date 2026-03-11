plugins {
    id("target.android.library")
    id("kotlin-parcelize")
}
dependencies {
    implementation(libs.koin.core)

    implementation(projects.tonapi.legacy)
    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.lib.blockchain)
}



