import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class WalletDataPlugin : AndroidLibraryPlugin() {

    override fun apply(target: Project) {
        super.apply(target)
        target.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
            implementation("io.insert-koin:koin-android:3.5.6")
        }
    }
}
