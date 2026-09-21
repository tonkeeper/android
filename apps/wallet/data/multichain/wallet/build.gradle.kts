plugins {
    id("target.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.chainkit.sdk)
    implementation(libs.chainkit.models)
    implementation(libs.chainkit.mnemonic)
    implementation(libs.walletcore)
    implementation(libs.kotlin.bignum)
    implementation(libs.kotlinx.serialization.json)

    implementation(projects.lib.blockchain)
    implementation(projects.tonapi.legacy)
    implementation(projects.tonapi.wallet)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.features)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.cache)
    implementation(projects.apps.wallet.data.rates)
    implementation(projects.apps.wallet.data.tokens)
    implementation(projects.apps.wallet.api)
    implementation(projects.lib.network)
    implementation(projects.lib.extensions)
    implementation(projects.lib.icu)
    implementation(projects.lib.wallet)
    implementation(projects.lib.security)
    implementation(projects.lib.bus)
    implementation(projects.kmp.async)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
}

tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().configureEach {
    enabled = true
}
