plugins {
    id("target.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(projects.tonapi.legacy)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.rates)
    implementation(projects.apps.wallet.data.collectibles)
    implementation(projects.apps.wallet.data.staking)
    implementation(projects.apps.wallet.api)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.extensions)
    implementation(projects.lib.icu)
    implementation(projects.lib.security)
    implementation(projects.lib.sqlite)

    implementation(libs.koin.core)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
