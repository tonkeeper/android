plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.androidx.biometric)
    implementation(libs.koin.core)
    implementation(libs.chainkit.models)

    implementation(projects.ui.uikit.core)
    implementation(projects.apps.wallet.localization)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.account)
    implementation(projects.apps.wallet.data.multichain.wallet)
    implementation(projects.apps.wallet.data.settings)
    implementation(projects.apps.wallet.data.rn)
    implementation(projects.lib.extensions)
    implementation(projects.lib.security)
    implementation(projects.lib.bus)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().configureEach {
    enabled = true
}
