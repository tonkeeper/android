import com.android.build.api.dsl.ManagedVirtualDevice

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

    testOptions {
        managedDevices {
            devices {
                create<ManagedVirtualDevice>("pixel7Api34") {
                    device = "Pixel 7"
                    apiLevel = 34
                    systemImageSource = "google"
                }
            }
        }
    }

    targetProjectPath = ":apps:wallet:instance:main"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    experimentalProperties["android.experimental.testOptions.managedDevices.setupTimeoutMinutes"] = 20
    experimentalProperties["android.experimental.androidTest.numManagedDeviceShards"] = 1
    experimentalProperties["android.experimental.testOptions.managedDevices.maxConcurrentDevices"] = 1
    experimentalProperties["android.experimental.testOptions.managedDevices.emulator.showKernelLogging"] = true
    experimentalProperties["android.testoptions.manageddevices.emulator.gpu"] = "swiftshader_indirect"
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.4")
}

baselineProfile {
    managedDevices += "pixel7Api34"
    useConnectedDevices = false
    enableEmulatorDisplay = false
}