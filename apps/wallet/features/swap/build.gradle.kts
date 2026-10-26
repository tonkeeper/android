plugins {
    id("target.android.compose")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    testOptions {
        unitTests {
            // android.util.Log and Looper are stubs here; the graph only asks them for defaults.
            isReturnDefaultValues = true
        }
    }
}

// The convention plugin disables host tests repo-wide. These are plain JUnit tests of the swap
// graph on virtual time, so they belong here rather than on a device.
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

    implementation(platform(libs.firebase.bom))

    implementation(libs.bundles.nav3)
    implementation(libs.kotlin.bignum)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.compose.paging)
    implementation(libs.compose.paging.runtime)
    implementation(libs.chainkit.models)

    implementation(projects.apps.wallet.api)
    implementation(projects.tonapi.exchange)
    implementation(projects.tonapi.wallet)
    implementation(projects.apps.wallet.data.raffle)
    implementation(projects.apps.wallet.data.multichain.wallet)
    implementation(projects.apps.wallet.data.multichain.exchange)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.backup)
    implementation(projects.apps.wallet.data.rn)
    implementation(projects.apps.wallet.data.features)
    implementation(projects.apps.wallet.features.core)

    implementation(projects.apps.wallet.localization)

    implementation(projects.kmp.core)
    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
    implementation(projects.kmp.async)

    implementation(projects.ui.uikit.icon)
    implementation(projects.ui.uikit.core)

    implementation(projects.lib.icu)
    implementation(projects.lib.bus)
    implementation(projects.lib.extensions)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.features)
    implementation(projects.lib.network)

    // Host tests (src/test) — the graph over recorded API responses, on virtual time.
    // junit, mockk and coroutines-test come with the testing module; wallet-core is jvm-only there.
    testImplementation(testFixtures(projects.apps.wallet.testing))
    testImplementation(libs.wallet.core.jvm)
}
