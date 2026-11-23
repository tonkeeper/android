plugins {
    id("target.android.library")
}

android {
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.webkit)

    implementation(projects.lib.log)
    implementation(projects.kmp.async)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(projects.lib.test))
}

tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().configureEach {
    enabled = true
}
