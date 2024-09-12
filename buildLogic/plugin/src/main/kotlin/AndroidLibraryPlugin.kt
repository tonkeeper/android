import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

open class AndroidLibraryPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.pluginManager.apply {
            apply("com.android.library")
            apply("org.jetbrains.kotlin.android")
        }

        target.extensions.configure<LibraryExtension> {
            compileSdk = 34

            defaultConfig {
                minSdk = 24
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            target.tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }
}
