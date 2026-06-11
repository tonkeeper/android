import plarform.defaultAndroidTarget
import plarform.defaultDependencies
import common.applyProjectCommon
import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
}

applyProjectCommon()

project.extensions.getByType(LibraryExtension::class.java).apply {
    defaultAndroidTarget()

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_21
        sourceCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

defaultDependencies()
