package plarform

import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.provider.Provider

class Deps(
    private val libs: VersionCatalog
) {
    fun coroutines(): Provider<MinimalExternalModuleDependency> {
        return libs.findLibrary("coroutines-core").get()
    }

    fun kotlinTest(): Provider<MinimalExternalModuleDependency> {
        return libs.findLibrary("kotlin-test").get()
    }

    fun coroutinesTest(): Provider<MinimalExternalModuleDependency> {
        return libs.findLibrary("coroutines-test").get()
    }

    fun androidTestRunner(): Provider<MinimalExternalModuleDependency> {
        return libs.findLibrary("androidx-test-runner").get()
    }
}