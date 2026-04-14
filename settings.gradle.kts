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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
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

    ":apps:wallet:features:core",
    ":apps:wallet:features:dapp",
    ":apps:wallet:features:ramp",
    ":apps:wallet:features:settings",
    ":apps:wallet:features:trading",
    ":apps:wallet:features:embeded:scanner",

    ":kmp:core",
    ":kmp:ui",
    ":kmp:async",
    ":kmp:mvi",
)
