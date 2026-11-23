import com.android.build.gradle.tasks.factory.AndroidUnitTest

plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)

    implementation(projects.lib.log)
    implementation(projects.lib.time)
    implementation(projects.lib.bus)
    implementation(projects.kmp.async)

    testImplementation(libs.junit)
}

tasks.withType<AndroidUnitTest>().configureEach {
    enabled = true
}
