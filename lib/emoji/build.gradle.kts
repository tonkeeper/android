
plugins {
    id("target.android.library")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.emoji)
    implementation(projects.kmp.async)
}