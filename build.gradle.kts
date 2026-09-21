import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.baselineprofile) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.firebase.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.performance) apply false
    alias(libs.plugins.ksp) apply false
}

//buildscript {
//    buildscript.configurations.configureEach {
//        resolutionStrategy.activateDependencyLocking()
//    }
//
//    dependencyLocking {
//        lockAllConfigurations()
////        lockMode.set(LockMode.STRICT)
//    }
//}

allprojects {
    configurations.all {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
        resolutionStrategy {
            force("org.bouncycastle:bcprov-jdk18on:1.80")
            force("org.bouncycastle:bcpkix-jdk18on:1.80")
            force("org.bouncycastle:bcutil-jdk18on:1.80")
        }
    }

    tasks.register("resolveAllDependencies") {
        notCompatibleWithConfigurationCache("Resolves configurations at execution time")
        doLast {
            configurations
                .filter { it.isCanBeResolved }
                .forEach {
                    runCatching { it.resolve() }
                        .onFailure { e -> logger.warn("Skip ${it.name}: ${e.message}") }
                }
        }
    }
}


allprojects {
    pluginManager.withPlugin("android") {
        configure<ApplicationExtension> {
            val localProperties = Properties().apply {
                rootProject.file("local.properties")
                    .takeIf { it.exists() }
                    ?.inputStream()
                    ?.use { load(it) }
            }

            fun findProperty(name: String): String? =
                localProperties[name]?.toString() ?: project.findProperty(name)?.toString()

            signingConfigs {
                create("release") {
                    val storeFilePath = findProperty("android.injected.signing.store.file")
                    if (storeFilePath != null) {
                        storeFile = file(storeFilePath)
                        storePassword = findProperty("android.injected.signing.store.password")
                        keyAlias = findProperty("android.injected.signing.key.alias")
                        keyPassword = findProperty("android.injected.signing.key.password")
                    }
                }

                getByName("debug") {
                    val debugFilePath = findProperty("android.injected.signing.debug.file")
                    if (debugFilePath != null) {
                        storeFile = file(debugFilePath)
                        storePassword = findProperty("android.injected.signing.debug.password")
                        keyAlias = findProperty("android.injected.signing.debug.key.alias")
                        keyPassword = findProperty("android.injected.signing.debug.key.password")
                    } else {
                        storeFile = file("${project.rootDir.path}/debug.keystore")
                        storePassword = "android"
                        keyAlias = "androiddebugkey"
                        keyPassword = "android"
                    }
                }
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(getLayout().buildDirectory)
}

// Install git hooks automatically on every Gradle run (worktree-aware)
val gitHooksDir = providers.exec {
    commandLine("git", "rev-parse", "--git-dir")
}.standardOutput.asText.get().trim().let { gitDir ->
    rootDir.resolve(gitDir).resolve("hooks")
}

copy {
    from(rootDir.resolve("tools/hooks/pre-commit"))
    into(gitHooksDir)
}

extensions.findByName("buildScan")?.withGroovyBuilder {
    setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
    setProperty("termsOfServiceAgree", "yes")
}
