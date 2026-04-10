plugins {
    id("target.android.compose")
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
    id("kotlinx-serialization")
}

android {
    namespace = "com.tonapps.tonkeeperx"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.koin.workmanager)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.j2objc)
    implementation(libs.cbor)
    implementation(libs.ton.tvm)
    implementation(libs.ton.crypto)
    implementation(libs.ton.tlb)
    implementation(libs.ton.blockTlb)
    implementation(libs.ton.tonapiTl)
    implementation(libs.ton.contract)

    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
    implementation(projects.kmp.async)

    implementation(projects.lib.bus)

    implementation(projects.apps.wallet.localization)
    implementation(projects.apps.wallet.api)

    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.tokens)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.rates)
    implementation(projects.apps.wallet.data.collectibles)
    implementation(projects.apps.wallet.data.events)
    implementation(projects.apps.wallet.data.browser)
    implementation(projects.apps.wallet.data.backup)
    implementation(projects.apps.wallet.data.rn)
    implementation(projects.apps.wallet.data.passcode)
    implementation(projects.apps.wallet.data.staking)
    implementation(projects.apps.wallet.data.purchase)
    implementation(projects.apps.wallet.data.battery)
    implementation(projects.apps.wallet.data.dapps)
    implementation(projects.apps.wallet.data.tx)
    implementation(projects.apps.wallet.data.contacts)
    implementation(projects.apps.wallet.data.swap)
    implementation(projects.apps.wallet.data.plugins)
    implementation(projects.apps.wallet.data.legacy)

    implementation(projects.apps.wallet.features.core)
    implementation(projects.apps.wallet.features.ramp)
    implementation(projects.apps.wallet.data.features)
    implementation(projects.lib.features)
    implementation(projects.apps.wallet.features.dapp)
    implementation(projects.apps.wallet.features.settings)
    implementation(projects.apps.wallet.features.trading)

    implementation(projects.ui.uikit.core)
    implementation(projects.ui.uikit.flag)

    implementation(libs.androidx.core)
    implementation(libs.androidx.shortcuts)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.recyclerView)
    implementation(libs.androidx.viewPager2)
    implementation(libs.androidx.workManager)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.lifecycle)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.browser)

    implementation(libs.material)
    implementation(libs.flexbox)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.performance)

    implementation(projects.tonapi.legacy)
    implementation(projects.ui.blur)

    implementation(projects.lib.network)
    implementation(projects.lib.icu)
    implementation(projects.lib.qr)
    implementation(projects.lib.log)
    implementation(projects.lib.emoji)
    implementation(projects.lib.security)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.extensions)
    implementation(projects.lib.ledger)
    implementation(projects.lib.ur)
    implementation(projects.lib.base64)

    implementation(libs.camerax.base)
    implementation(libs.camerax.core)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.google.play.review)
    implementation(libs.google.play.billing)
    implementation(libs.google.play.update)
    implementation(libs.google.play.installreferrer)

    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.aptabase)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.foundationLayout)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.preview)
    implementation(libs.compose.paging)
    implementation(libs.compose.paging.runtime)
    debugImplementation(libs.compose.debugTooling)


    implementation(libs.compose.viewModel)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${buildDir}/compose_metrics",
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${buildDir}/compose_reports"
        )
    }
}
