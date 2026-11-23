plugins {
    id("target.android.library")
    id("kotlin-parcelize")
}
dependencies {
    implementation(libs.koin.core)

    implementation(projects.tonapi.legacy)
    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.lib.blockchain)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().configureEach {
    enabled = true
}
