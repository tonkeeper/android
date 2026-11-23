plugins {
    id("target.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

tasks.withType<Test>().configureEach {
    enabled = true
}

configurations.matching { it.name.startsWith("testDebug") || it.name.startsWith("testRelease") }
    .configureEach {
        exclude(group = "com.trustwallet", module = "wallet-core-kotlin-android")
    }

dependencies {
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.bignum)
    implementation(libs.chainkit.sdk)
    implementation(libs.chainkit.models)
    implementation(libs.chainkit.api)
    implementation(libs.chainkit.chain.bitcoin)
    implementation(libs.walletcore)
    implementation(libs.okhttp)

    api(projects.lib.ledgerkit)
    implementation(projects.lib.ledger)
    implementation(projects.lib.wallet)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.bus)
    implementation(projects.lib.log)
    implementation(projects.lib.time)
    implementation(projects.lib.extensions)
    implementation(projects.kmp.async)
    implementation(projects.apps.wallet.data.multichain.wallet)
    implementation(projects.apps.wallet.api)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.wallet.core.jvm)
    testImplementation(libs.web3j)
    testImplementation(libs.bcprovjdk)
    testImplementation(libs.mockk)
}
