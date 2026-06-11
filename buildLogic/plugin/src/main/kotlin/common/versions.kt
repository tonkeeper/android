package common

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

fun Project.versionCatalog(): VersionCatalog {
    return extensions.getByType<VersionCatalogsExtension>()
        .named("libs")
}