import org.gradle.api.Plugin
import org.gradle.api.Project

open class AndroidLibraryPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.pluginManager.apply {
            apply("com.android.library")
        }
    }
}
