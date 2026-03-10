import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.okio)

    implementation(projects.lib.log)
}