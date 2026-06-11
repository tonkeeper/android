plugins {
    id("target.android.library")
}

android {
    namespace = "com.facebook.shimmer"
}

dependencies {
    implementation(libs.androidx.annotation)
}