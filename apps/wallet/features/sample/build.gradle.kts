plugins {
    id("target.android.compose")
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.nav3)
    implementation(libs.kotlinx.coroutines.android)

    implementation(projects.kmp.core)
    implementation(projects.kmp.ui)
    implementation(projects.kmp.mvi)
}
