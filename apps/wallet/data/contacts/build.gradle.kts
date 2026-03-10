plugins {
    id("target.android.library")
    id("kotlin-parcelize")
}

dependencies {
    implementation(libs.koin.core)

    implementation(projects.apps.wallet.data.core)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.extensions)
    implementation(projects.lib.sqlite)
    implementation(projects.apps.wallet.data.rn)
}
