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
    api(libs.kotlin.bignum)
    api(libs.kotlinx.io.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.bcprovjdk)
    implementation(libs.web3j)
    implementation("org.bitcoinj:bitcoinj-core:0.15.10") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    implementation("com.google.protobuf:protobuf-javalite:3.25.5")

    implementation(projects.apps.wallet.localization) // TODO
    implementation(projects.lib.extensions)
    implementation(projects.lib.base64)
    implementation(projects.lib.icu)
    implementation(projects.lib.security)
    implementation(projects.ui.uikit.flag)
    implementation(projects.ui.uikit.icon)

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    enabled = true
}
