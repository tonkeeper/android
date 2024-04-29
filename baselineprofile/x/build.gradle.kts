plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = Build.namespacePrefix("x.baselineprofile")
    compileSdk = Build.compileSdkVersion

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    targetProjectPath = ":apps:wallet:instance"
}

dependencies {
    implementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("androidx.test.uiautomator:uiautomator:2.2.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.2")
}