plugins {
    id("target.android.library")
}

// The convention plugin disables host tests repo-wide. SwapDetailsTest is a plain JUnit test of how
// Swap details reads a history entry.
tasks.withType<Test>().configureEach {
    enabled = true
}

configurations.matching { it.name.startsWith("testDebug") || it.name.startsWith("testRelease") }
    .configureEach {
        exclude(group = "com.trustwallet", module = "wallet-core-kotlin-android")
    }

dependencies {
    implementation(libs.koin.core)
    implementation(libs.chainkit.sdk)
    implementation(libs.chainkit.swap)
    implementation(libs.chainkit.op)
    implementation(libs.kotlin.bignum)
    implementation(libs.kotlinx.serialization.json)

    implementation(projects.apps.wallet.api)
    implementation(projects.tonapi.wallet)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.multichain.wallet)
    implementation(projects.apps.wallet.data.features)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.tonapi.exchange)
    implementation(projects.lib.extensions)
    implementation(projects.lib.features)
    implementation(projects.kmp.async)

    testImplementation(testFixtures(projects.apps.wallet.testing))
    testImplementation(libs.wallet.core.jvm)
}
