import java.util.Properties
import kotlin.apply

pluginManagement {
    includeBuild("buildLogic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://plugins.gradle.org/m2/")
    }
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootDir.resolve("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

fun getProperty(key: String): String {
    return localProperties.getProperty(key)
        ?: throw GradleException("Key `$key` is undefine, Please add it to local.properties!",)
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    url = uri("https://maven.pkg.github.com/trustwallet/wallet-core")
                    credentials {
                        username = getProperty("GITHUB_USER")
                        password = getProperty("GITHUB_TOKEN")
                    }
                }
            }

            filter {
                includeGroup("com.trustwallet")
            }
        }

        exclusiveContent {
            forRepository {
                maven {
                    setUrl(file(rootDir.resolve("mavenLocal/publish")))
                }
            }

            forRepository {
                maven {
                    url = uri("https://maven.pkg.github.com/tonkeeper/chainkit-publishing")
                    credentials {
                        username = runCatching { getProperty("CHAINKIT_GITHUB_USER") }.getOrNull()
                            ?: getProperty("GITHUB_USER")
                        password = runCatching { getProperty("CHAINKIT_GITHUB_TOKEN") }.getOrNull()
                            ?: getProperty("GITHUB_TOKEN")
                    }
                }
            }

            filter {
                includeGroup("com.tonapps.chainkit")
            }
        }

        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://plugins.gradle.org/m2/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

buildCache {
    //https://docs.gradle.org/current/userguide/build_cache.html#sec:build_cache_configure_remote
    local {
        isEnabled = true
        directory = rootDir.resolve(".build-cache")
    }
}

rootProject.name = "Tonkeeper"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
include(
    ":apps:signer",
    ":baselineprofile:main",

    ":tonapi:core",
    ":tonapi:tonkeeper",
    ":tonapi:battery",
    ":tonapi:exchange",
    ":tonapi:trading",
    ":tonapi:perps",
    ":tonapi:kandelabr",
    ":tonapi:wallet",
    ":tonapi:legacy",

    ":ui:shimmer",
    ":ui:blur",
    ":ui:uikit:core",
    ":ui:uikit:color",
    ":ui:uikit:icon",
    ":ui:uikit:list",
    ":ui:uikit:flag",

    ":lib:extensions",
    ":lib:security",
    ":lib:network",
    ":lib:qr",
    ":lib:log",
    ":lib:icu",
    ":lib:emoji",
    ":lib:blockchain",
    ":lib:sqlite",
    ":lib:ledger",
    ":lib:ur",
    ":lib:base64",
    ":lib:bus",
    ":lib:wallet-kit",
    ":lib:wallet",
    ":lib:wc",
    ":lib:features",

    ":apps:wallet:instance:app",
    ":apps:wallet:instance:main",
    ":apps:wallet:localization",
    ":apps:wallet:api",
    ":apps:wallet:data:core",
    ":apps:wallet:data:legacy",
    ":apps:wallet:data:settings",
    ":apps:wallet:data:account",
    ":apps:wallet:data:rates",
    ":apps:wallet:data:tokens",
    ":apps:wallet:data:events",
    ":apps:wallet:data:collectibles",
    ":apps:wallet:data:browser",
    ":apps:wallet:data:banner",
    ":apps:wallet:data:raffle",
    ":apps:wallet:data:backup",
    ":apps:wallet:data:tx",
    ":apps:wallet:data:rn",
    ":apps:wallet:data:passcode",
    ":apps:wallet:data:staking",
    ":apps:wallet:data:purchase",
    ":apps:wallet:data:battery",
    ":apps:wallet:data:dapps",
    ":apps:wallet:data:contacts",
    ":apps:wallet:data:swap",
    ":apps:wallet:data:plugins",
    ":apps:wallet:data:features",
    ":apps:wallet:data:cache",

    ":apps:wallet:data:multichain:wallet",
    ":apps:wallet:data:multichain:exchange",

    ":apps:wallet:features:core",
    ":apps:wallet:features:dapp",
    ":apps:wallet:features:onboarding",
    ":apps:wallet:features:ramp",
    ":apps:wallet:features:settings",
    ":apps:wallet:features:trading",
    ":apps:wallet:features:swap",
    ":apps:wallet:features:portfolio",
    ":apps:wallet:features:migration",
    ":apps:wallet:features:perps",
    ":apps:wallet:features:events",
    ":apps:wallet:features:embeded:scanner",

    ":kmp:core",
    ":kmp:ui",
    ":kmp:async",
    ":kmp:mvi",
    ":kmp:chart",
)
