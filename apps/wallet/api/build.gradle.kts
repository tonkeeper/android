plugins {
    id("target.android.library")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    kotlin("plugin.serialization")
}

android {
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
//    configurations.all {
//        exclude(mapOf("group" to "org.chromium.net", "module" to "cronet-shared"))
//    }
//    implementation(libs.cronet.okhttp)
//    implementation(libs.google.play.cronet)

    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.core)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.centrifuge.java)
    implementation(libs.koin.core)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    api(libs.chainkit.models)

    implementation(projects.tonapi.legacy)
    implementation(projects.tonapi.wallet)
    implementation(projects.tonapi.perps)
    implementation(projects.tonapi.kandelabr)
    implementation(projects.tonapi.earn)
    implementation(projects.tonapi.tonendpoint)
    implementation(projects.lib.network)
    implementation(projects.lib.time)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.wallet)
    implementation(projects.lib.extensions)
    implementation(projects.lib.icu)
    implementation(projects.lib.log)
    implementation(projects.lib.bus)
    implementation(projects.lib.features)
    implementation(projects.apps.wallet.data.features)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // android.jar only stubs org.json: a jvm test parses with the real one.
    testImplementation(libs.org.json)
}

tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().configureEach {
    enabled = true
}
