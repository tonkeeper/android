plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.appsflyer.sdk)

    implementation(projects.kmp.async)
    implementation(projects.lib.bus)
    implementation(projects.lib.log)

    testImplementation(libs.junit)
}

tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().configureEach {
    enabled = true
}
