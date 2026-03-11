@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.baselineprofile)
    alias(libs.plugins.android.test)
}

val isCI = project.hasProperty("android.injected.signing.store.file")

android {
    namespace = "com.tonapps.main.baselineprofile"
    compileSdk = libs.versions.android.sdk.compile.get().toInt()

    defaultConfig {
        testInstrumentationRunnerArguments += mapOf("suppressErrors" to "EMULATOR")
        minSdk = libs.versions.android.sdk.min.get().toInt()
        targetSdk = libs.versions.android.sdk.target.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    flavorDimensions += listOf("version")

    productFlavors {
        create("default") { dimension = "version" }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    testOptions.managedDevices {
        localDevices.create("pixel6Api33") {
            device = "Pixel 6"
            apiLevel = 33
            systemImageSource = "google"
        }
    }

    targetProjectPath = ":apps:wallet:instance:main"

    experimentalProperties["android.experimental.self-instrumenting"] = true
    experimentalProperties["android.experimental.testOptions.managedDevices.setupTimeoutMinutes"] = 20
    experimentalProperties["android.experimental.androidTest.numManagedDeviceShards"] = 1
    experimentalProperties["android.experimental.testOptions.managedDevices.maxConcurrentDevices"] = 1
    experimentalProperties["android.experimental.testOptions.managedDevices.emulator.showKernelLogging"] = true
    if (isCI) {
        experimentalProperties["android.testoptions.manageddevices.emulator.gpu"] = "swiftshader_indirect"
    }
}

dependencies {
    implementation(libs.androidx.test.core)
    implementation(libs.androidx.test.espresso)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark)
}

baselineProfile {
    // managedDevices += "pixel6Api33"
    // useConnectedDevices = false
    enableEmulatorDisplay = !isCI
}


androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        val testedApks = v.testedApks.map {
            artifactsLoader.load(it)?.applicationId ?: "com.ton_keeper"
        }
        v.instrumentationRunnerArguments.put("targetAppId", testedApks)
    }
}