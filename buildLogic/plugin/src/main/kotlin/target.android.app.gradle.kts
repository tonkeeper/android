import plarform.applyAndroidApp
import plarform.defaultDependencies
import common.applyProjectCommon
import plarform.androidNamespace

plugins {
    id("com.android.application")
}

applyProjectCommon()

android {
    applyAndroidApp()

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_21
        sourceCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

defaultDependencies()
