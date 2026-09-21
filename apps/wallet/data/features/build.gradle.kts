plugins {
    id("target.android.library")
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)

    api(projects.lib.features)
    implementation(projects.lib.log)
    implementation(projects.lib.features)
}
