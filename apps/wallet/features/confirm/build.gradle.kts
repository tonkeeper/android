plugins {
    id("target.android.compose")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    testOptions {
        unitTests {
            // android.util.Log and Looper are stubs here; features only ask them for defaults.
            isReturnDefaultValues = true
        }
    }
}

// The convention plugin disables host tests repo-wide. These are plain JUnit tests on virtual time,
// so they belong here rather than on a device.
tasks.withType<Test>().configureEach {
    enabled = true
}

// wallet-core ships the native lib per platform: the aar's .so cannot load in a JVM test, and the
// jvm jar unpacks its own. Keep the aar off the unit-test classpath so the jvm loader wins.
configurations.matching { it.name.startsWith("testDebug") || it.name.startsWith("testRelease") }
    .configureEach {
        exclude(group = "com.trustwallet", module = "wallet-core-kotlin-android")
    }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.debugTooling)

    implementation(libs.chainkit.models)
    implementation(libs.chainkit.api)
    implementation(libs.chainkit.sdk)
    implementation(libs.chainkit.op)

    implementation(libs.kotlin.bignum)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)

    implementation(projects.tonapi.legacy)
    implementation(projects.tonapi.battery)

    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.localization)

    implementation(projects.apps.wallet.data.multichain.wallet)
    implementation(projects.apps.wallet.data.multichain.exchange)

    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.tokens)
    implementation(projects.apps.wallet.data.battery)
    implementation(projects.apps.wallet.data.passcode)
    implementation(projects.apps.wallet.data.dapps)
    implementation(projects.apps.wallet.data.tx)

    implementation(projects.apps.wallet.features.core)

    implementation(projects.kmp.core)
    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
    implementation(projects.kmp.async)

    implementation(projects.ui.uikit.icon)
    implementation(projects.ui.uikit.core)

    implementation(projects.lib.bus)
    implementation(projects.lib.icu)
    implementation(projects.lib.log)
    implementation(projects.lib.security)
    implementation(projects.lib.wallet)
    implementation(projects.lib.ledger)
    implementation(projects.lib.extensions)
    implementation(projects.lib.blockchain)

    // Host tests (src/test) — junit, mockk and coroutines-test come with the testing module.
    testImplementation(testFixtures(projects.apps.wallet.testing))
    testImplementation(libs.wallet.core.jvm)
    // Recorded swap responses decode into the generated exchange models.
    testImplementation(projects.tonapi.exchange)
    // android.jar's org.json is a stub returning null; confirm analytics build their metadata with it.
    testImplementation("org.json:json:20240303")
}
