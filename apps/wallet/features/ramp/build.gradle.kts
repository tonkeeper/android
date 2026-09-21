plugins {
    id("target.android.compose")
    id("kotlin-parcelize")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    defaultConfig {
        // Enables instrumented tests (src/androidTest). Host unit tests stay disabled by the
        // convention plugin, but instrumented tests need TrustWalletCore's native lib, so they
        // must run on a device/emulator anyway.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources {
            // Test-only deps (mockk / junit transitive jars) ship duplicate license metadata.
            excludes += setOf(
                "/META-INF/LICENSE.md",
                "/META-INF/LICENSE-notice.md",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/AL2.0",
                "/META-INF/LGPL2.1",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.debugTooling)

    implementation(libs.chainkit.models)
    implementation(libs.chainkit.api)
    implementation(libs.chainkit.sdk)

    implementation(libs.compose.paging)
    implementation(libs.compose.paging.runtime)

    implementation(libs.bundles.nav3)
    implementation(libs.kotlin.bignum)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.coil.compose) // TODO remove, use from DesSys

    implementation(projects.tonapi.legacy)

    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.localization)

    implementation(projects.apps.wallet.data.multichain.wallet)
    implementation(projects.apps.wallet.data.multichain.exchange)

    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.tokens)
    implementation(projects.apps.wallet.data.rates)
    implementation(projects.apps.wallet.data.battery)
    implementation(projects.apps.wallet.data.contacts)
    implementation(projects.apps.wallet.data.events)
    implementation(projects.apps.wallet.data.rn)
    implementation(projects.apps.wallet.data.passcode)
    implementation(projects.apps.wallet.data.collectibles)

    implementation(projects.apps.wallet.data.tx)

    implementation(projects.tonapi.exchange)
    implementation(projects.tonapi.battery)
    implementation(projects.apps.wallet.data.legacy)

    implementation(projects.apps.wallet.features.core)
    implementation(projects.apps.wallet.features.dapp)
    implementation(projects.apps.wallet.features.embeded.scanner)
    implementation(projects.apps.wallet.data.dapps)

    implementation(projects.kmp.core)
    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
    implementation(projects.kmp.async)

    implementation(projects.ui.uikit.icon)
    implementation(projects.ui.uikit.core)

    implementation(projects.lib.qr)
    implementation(projects.lib.bus)
    implementation(projects.lib.icu)
    implementation(projects.lib.security)
    implementation(projects.lib.security)
    implementation(projects.lib.wallet)
    implementation(projects.lib.ledger)
    implementation(projects.lib.extensions)
    implementation(projects.lib.blockchain)

    // Instrumented tests (src/androidTest) — build & encode real swap transactions on-device.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockk.android)
    // Reach the chainkit build path (mediator/SignDelegate) and swap payload parser directly.
    androidTestImplementation(libs.chainkit.sdk)
    androidTestImplementation(libs.chainkit.api)
    androidTestImplementation(libs.chainkit.models)
    // The unsigned SigningInput is a Wire protobuf Message; expose the type to the test classpath.
    androidTestImplementation("com.squareup.wire:wire-runtime:4.5.6")
}
