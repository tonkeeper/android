plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.koin.core)

    testImplementation(libs.junit)
}

tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().configureEach {
    enabled = true
}
