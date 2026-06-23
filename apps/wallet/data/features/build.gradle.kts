plugins {
    id("target.android.library")
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)

    implementation(projects.lib.log)
    implementation(projects.lib.features)
}
