import com.android.build.gradle.AppExtension

plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("com.android.library") version "8.5.2" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
    id("com.android.test") version "8.5.2" apply false
    id("androidx.baselineprofile") version "1.2.4"
    id("org.jetbrains.kotlin.jvm") version "1.9.0" apply false
}

allprojects {
    pluginManager.withPlugin("android") {
        configure<AppExtension> {
            signingConfigs {
                create("release") {
                    if (project.hasProperty("android.injected.signing.store.file")) {
                        storeFile = file(project.property("android.injected.signing.store.file").toString())
                        storePassword = project.property("android.injected.signing.store.password").toString()
                        keyAlias = project.property("android.injected.signing.key.alias").toString()
                        keyPassword = project.property("android.injected.signing.key.password").toString()
                    }
                }

                getByName("debug") {
                    storeFile = file("${project.rootDir.path}/${Signing.Debug.storeFile}")
                    storePassword = Signing.Debug.storePassword
                    keyAlias = Signing.Debug.keyAlias
                    keyPassword = Signing.Debug.keyPassword
                }
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(getLayout().buildDirectory)
}
