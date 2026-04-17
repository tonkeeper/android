package plarform

import common.versionCatalog
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

@OptIn(ExperimentalKotlinGradlePluginApi::class)
fun Project.defaultDependencies(
    commonDependencies: Deps = Deps(versionCatalog()),
) {
    dependencies {
//        add("implementation", commonDependencies.coroutines())
//        add("testImplementation", commonDependencies.kotlinTest())
//        add("testImplementation", commonDependencies.coroutinesTest())
    }
}
