plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = Build.namespacePrefix("main.baselineprofile")
    compileSdk = Build.compileSdkVersion

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    targetProjectPath = ":apps:wallet:instance:main"

    testOptions.managedDevices.devices {
        create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api31") {
            device = "Pixel 6"
            apiLevel = 31
            systemImageSource = "aosp"
        }
    }
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.4")
}

baselineProfile {
    managedDevices += "pixel6Api31"
    useConnectedDevices = false
}