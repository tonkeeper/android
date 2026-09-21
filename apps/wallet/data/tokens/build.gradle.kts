plugins {
    id("target.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(libs.koin.core)

    implementation(projects.lib.blockchain)
    implementation(projects.tonapi.legacy)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.rates)
    implementation(projects.apps.wallet.api)
    implementation(projects.lib.security)
    implementation(projects.lib.extensions)
    implementation(projects.lib.icu)


    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
