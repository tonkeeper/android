plugins {
    id("target.android.library")
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ./gradlew :lib:icu:atdApi36DebugAndroidTest - Gradle fetches the image and runs it headless.
    testOptions.managedDevices {
        localDevices.create("atdApi36") {
            device = "Pixel 6"
            apiLevel = 36
            systemImageSource = "aosp-atd"
        }
    }
}

dependencies {
    implementation(libs.okio)

    implementation(projects.lib.log)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
}
