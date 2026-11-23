import com.android.build.gradle.tasks.factory.AndroidUnitTest

plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.androidx.core)

    testImplementation(libs.junit)
}

tasks.withType<AndroidUnitTest>().configureEach {
    enabled = true
}
