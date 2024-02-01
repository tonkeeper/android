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

rootProject.name = "TON Apps"

include(":apps:x")
include(":apps:signer")

include(":baselineprofile:x")
include(":baselineprofile:signer")

include(":network")
include(":qr")
include(":ton")
include(":core")
include(":tonapi")
include(":localization")


include(":ui:uikit")
include(":ui:shimmer")
include(":ui:blur")

include(":lib:security")
