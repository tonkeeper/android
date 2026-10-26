plugins {
    id("target.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    // android.util.Log, which L writes to, is a stub in unit tests.
    testOptions.unitTests.isReturnDefaultValues = true
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

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
}

tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().configureEach {
    enabled = true
}
