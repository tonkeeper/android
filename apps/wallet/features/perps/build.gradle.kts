plugins {
    id("target.android.compose")
    id("kotlin-parcelize")
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

    implementation(libs.bundles.nav3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.compose.paging)
    implementation(libs.compose.paging.runtime)

    implementation(libs.chainkit.sdk)
    implementation(libs.chainkit.op)
    implementation(libs.chainkit.models)
    implementation(libs.chainkit.chain.ethereum)
    implementation(libs.chainkit.net.json)
    implementation(libs.walletcore)
    implementation(libs.bcprovjdk)

    implementation(projects.apps.wallet.api)
    implementation(projects.tonapi.perps)
    implementation(projects.tonapi.kandelabr)
    implementation(projects.tonapi.trading)

    implementation(projects.apps.wallet.features.core)
    implementation(projects.apps.wallet.features.confirm)
    implementation(projects.apps.wallet.localization)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.multichain.wallet)
    implementation(projects.apps.wallet.data.passcode)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.lib.wallet)

    implementation(projects.kmp.core)
    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
    implementation(projects.kmp.async)
    implementation(projects.kmp.chart)

    implementation(projects.ui.uikit.icon)
    implementation(projects.ui.uikit.core)

    implementation(projects.lib.blockchain)
    implementation(projects.lib.bus)
    implementation(projects.lib.security)
    implementation(projects.lib.extensions)
    implementation(projects.lib.log)

    // Host tests (src/test) — junit, mockk and coroutines-test come with the testing module.
    testImplementation(testFixtures(projects.apps.wallet.testing))
    testImplementation(libs.wallet.core.jvm)
    // android.jar's org.json is a stub returning null; confirm analytics build their metadata with it.
    testImplementation("org.json:json:20240303")
}
