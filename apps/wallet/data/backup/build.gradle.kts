plugins {
    id("target.android.library")
    id("kotlin-parcelize")
}

dependencies {
    implementation(libs.koin.core)

    implementation(projects.lib.sqlite)
    implementation(projects.lib.extensions)
    implementation(projects.apps.wallet.data.rn)
}

