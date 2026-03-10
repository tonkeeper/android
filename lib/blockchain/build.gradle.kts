import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("target.android.library")
    id("kotlin-parcelize")
}

dependencies {
    api(platform(libs.firebase.bom))
    api(libs.firebase.crashlytics)

    api(libs.ton.tvm)
    api(libs.ton.crypto)
    api(libs.ton.tlb)
    api(libs.ton.blockTlb)
    api(libs.ton.tonapiTl)
    api(libs.ton.contract)
    api(libs.kotlinx.io.core)
    implementation(projects.lib.extensions)
    implementation(projects.lib.base64)
    implementation(libs.bcprovjdk)
    implementation(libs.web3j) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    }
    implementation("org.bitcoinj:bitcoinj-core:0.15.10") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    implementation("com.google.protobuf:protobuf-javalite:3.25.5")
}


