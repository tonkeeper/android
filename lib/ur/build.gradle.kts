plugins {
    id("target.android.library")
}

dependencies {
    implementation(projects.lib.extensions)
    implementation(libs.cbor)
}

