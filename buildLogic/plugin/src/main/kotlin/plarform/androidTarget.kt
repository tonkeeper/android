package plarform

import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import common.applyProjectCommon
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

// Data classes for Android configuration (from android/androidConfig.kt)

data class AndroidVersion(
    val minSdk: Int,
    val compileSdk: Int,
    val targetSdk: Int,
    val buildTools: String,
)

data class LibVersions(
    val composeVersion: String,
    val desugaringVersion: String
)

data class AndroidConfiguration(
    val appVersions: AndroidVersion,
    val libVersions: LibVersions
)

fun Project.androidDefaultConfig(): AndroidConfiguration {
    val libs: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
    val androidConf = androidConfig(libs)
    return androidConf
}

fun androidConfig(libs: VersionCatalog): AndroidConfiguration {
    return AndroidConfiguration(
        appVersions = AndroidVersion(
            minSdk = libs.findVersion("android-sdk-min").get().requiredVersion.toInt(),
            compileSdk = libs.findVersion("android-sdk-compile").get().requiredVersion.toInt(),
            targetSdk = libs.findVersion("android-sdk-target").get().requiredVersion.toInt(),
            buildTools = libs.findVersion("android-sdk-tools").get().requiredVersion
        ),
        libVersions = LibVersions(
            composeVersion = "1",
            desugaringVersion = "1"
        )
    )
}

// Android library target configuration (from android/androidLibrary.kt)
context(target: LibraryExtension)
fun Project.defaultAndroidTarget(
    versions: AndroidVersion = androidDefaultConfig().appVersions,
    namespace: String = "com.tonapps.",
    setup: LibraryExtension.() -> Unit = {},
) {
    target.apply {
        applyProjectCommon()
        applyAndroidCommon(
            androidNamespace(namespace),
            versions,
        )

        setup()
    }
}

context(target: ApplicationExtension)
fun Project.applyAndroidApp(
    versions: AndroidVersion = androidDefaultConfig().appVersions,
    testRunner: String? = null,
    manifestPlaceholders: Map<String, Any> = emptyMap(),
    configSetup: ApplicationDefaultConfig.() -> Unit = {},
) {
    target.apply {
        compileSdk = versions.compileSdk
        buildToolsVersion = versions.buildTools

        defaultConfig {
            minSdk = versions.minSdk
            targetSdk = versions.targetSdk

            addManifestPlaceholders(manifestPlaceholders)
            testRunner?.let {
                testInstrumentationRunner = it
            }

            configSetup()
        }
    }
}

fun Project.androidNamespace(commonPackage: String): String =
    commonPackage + path.trim(':')
        .replace(':', '.')
        .replace("-", "")

fun LibraryExtension.applyAndroidCommon(
    namespace: String,
    versions: AndroidVersion,
) {
    this.namespace = namespace
    applyAndroidCommonBase(versions)
}

fun LibraryExtension.applyAndroidCommonBase(
    versions: AndroidVersion,
) {
    defaultConfig {
        minSdk = versions.minSdk
        compileSdk = versions.compileSdk
    }

    buildToolsVersion = versions.buildTools
}
