plugins {
    id("target.android.library")
    id("kotlin-parcelize")
    kotlin("plugin.serialization")
}

dependencies {
    api(platform(libs.firebase.bom))
    api(libs.firebase.crashlytics)
    api(libs.androidx.core)

    api(libs.ton.tvm)
    api(libs.ton.crypto)
    api(libs.ton.tlb)
    api(libs.ton.blockTlb)
    api(libs.ton.tonapiTl)
    api(libs.ton.contract)
    api(libs.chainkit.models)
    api(libs.chainkit.op)
    api(libs.kotlin.bignum)
    api(libs.kotlinx.io.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.chainkit.chain.tron)
    implementation(libs.chainkit.chain.solana)
    implementation(libs.okio)
    implementation("com.google.protobuf:protobuf-javalite:4.36.2")

    implementation(projects.apps.wallet.localization) // TODO
    implementation(projects.lib.extensions)
    implementation(projects.lib.base64)
    implementation(projects.lib.bus)
    implementation(projects.lib.icu)
    implementation(projects.lib.security)
    implementation(projects.ui.uikit.flag)
    implementation(projects.ui.uikit.icon)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.wallet.core.jvm)
}

// wallet-core ships the native lib per platform: the aar's .so cannot load in a JVM test, and the
// jvm jar unpacks its own. Keep the aar off the unit-test classpath so the jvm loader wins.
configurations.matching { it.name.startsWith("testDebug") || it.name.startsWith("testRelease") }
    .configureEach {
        exclude(group = "com.trustwallet", module = "wallet-core-kotlin-android")
    }

tasks.withType<Test>().configureEach {
    enabled = true
}
