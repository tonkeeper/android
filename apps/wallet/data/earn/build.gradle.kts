plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.koin.core)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.cache)
    implementation(projects.tonapi.earn)

    implementation(projects.lib.log)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}

tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().configureEach {
    enabled = true
}
