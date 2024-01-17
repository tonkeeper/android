pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Tonkeeper R"

include(":apps:x")
include(":apps:singer")

include(":ton")
include(":core")
include(":tonapi")
include(":localization")
include(":baselineprofile")
include(":argon2kt")

include(":ui:uikit")
include(":ui:shimmer")
include(":ui:blur")