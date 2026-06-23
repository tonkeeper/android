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
    implementation(libs.koin.core)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(projects.tonapi.legacy)
    implementation(projects.lib.network)
    implementation(projects.lib.blockchain)
    implementation(projects.lib.extensions)
    implementation(projects.lib.icu)
    implementation(projects.lib.log)
    implementation(projects.lib.features)
    implementation(projects.apps.wallet.data.features)
}
