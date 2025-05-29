plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uikit"
    compileSdk = Build.compileSdkVersion
    defaultConfig {
        minSdk = Build.minSdkVersion
        consumerProguardFiles("consumer-rules.pro")
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    api(project(Dependence.UIKit.color))
    api(project(Dependence.UIKit.icon))
    api(project(Dependence.UIKit.list))
    api(project(Dependence.Module.shimmer))

    implementation(Dependence.KotlinX.coroutines)
    implementation(Dependence.AndroidX.core)
    implementation(Dependence.AndroidX.webkit)
    implementation(Dependence.AndroidX.activity)
    implementation(Dependence.AndroidX.fragment)
    implementation(Dependence.AndroidX.appCompat)
    implementation(Dependence.AndroidX.splashscreen)
    implementation(Dependence.UI.flexbox)
    implementation(Dependence.UI.material)
    implementation(Dependence.fresco) {
        exclude(group = "com.facebook.soloader", module = "soloader")
        exclude(group = "com.facebook.fresco", module = "soloader")
        exclude(group = "com.facebook.fresco", module = "nativeimagefilters")
        exclude(group = "com.facebook.fresco", module = "nativeimagetranscoder")
        exclude(group = "com.facebook.fresco", module = "memory-type-native")
        exclude(group = "com.facebook.fresco", module = "imagepipeline-native")
    }

    implementation(platform(Dependence.AndroidX.Compose.bom))
    implementation(Dependence.AndroidX.Compose.foundation)
    implementation(Dependence.AndroidX.Compose.foundationLayout)
    implementation(Dependence.AndroidX.Compose.ui)
    implementation(Dependence.AndroidX.Compose.material3)
    implementation(Dependence.AndroidX.Compose.preview)
    debugImplementation(Dependence.AndroidX.Compose.debugTooling)
}
