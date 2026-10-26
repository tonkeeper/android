plugins {
    id("target.android.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.branch.sdk)
    implementation(libs.linkrunner.sdk)

    implementation(projects.kmp.async)
    implementation(projects.lib.bus)
    implementation(projects.lib.log)

    testImplementation(libs.junit)
}

tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().configureEach {
    enabled = true
}
