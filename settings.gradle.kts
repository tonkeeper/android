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
include(":app")
include(":ton")
include(":core")
include(":tonapi")
include(":localization")
include(":baselineprofile")

include(":ui:uikit")
include(":ui:shimmer")
include(":ui:blur")