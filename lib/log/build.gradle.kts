plugins {
    id("target.android.library")
}

android {
    defaultConfig {
        buildFeatures {
            buildConfig = true
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
