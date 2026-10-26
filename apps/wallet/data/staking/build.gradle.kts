plugins {
    id("target.android.library")
    id("kotlin-parcelize")
}

android {
    val sharedResources = rootProject.file(
        "${if (rootProject.file("resources").isDirectory) { "resources" } else { "../shared/resources" }}/Sources/KeeperResources/Resources"
    )
    sourceSets {
        named("main") {
            res.srcDirs(sharedResources.resolve("Images"))
        }
    }
}

dependencies {
    implementation(projects.lib.blockchain)
    implementation(projects.lib.extensions)
    implementation(projects.lib.icu)
    implementation(libs.koin.core)

    implementation(projects.tonapi.legacy)

    implementation(projects.apps.wallet.api)
    implementation(projects.apps.wallet.data.core)
    implementation(projects.apps.wallet.data.tokens)

    testImplementation(libs.junit)
}

tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().configureEach {
    enabled = true
}
