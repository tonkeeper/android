plugins {
    id("target.android.compose")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "uikit"
}

dependencies {
    api(projects.ui.uikit.color)
    api(projects.ui.uikit.icon)
    api(projects.ui.uikit.list)
    api(projects.ui.blur)
    api(projects.ui.shimmer)
    implementation(projects.lib.log)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.splashscreen)
    implementation(libs.flexbox)
    implementation(libs.material)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.foundationLayout)
    implementation(libs.compose.ui)
}
