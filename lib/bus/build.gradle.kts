import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.tasks.factory.AndroidUnitTest

plugins {
    id("target.android.library")
    id("kotlin-parcelize")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(projects.lib.extensions)
    implementation(projects.lib.features)
    implementation(projects.kmp.async)

    // Analytic aptabase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.aptabase)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

extensions.configure<LibraryExtension> {
    testOptions.unitTests.isReturnDefaultValues = true
}

tasks.withType<AndroidUnitTest>().configureEach {
    enabled = true
}
