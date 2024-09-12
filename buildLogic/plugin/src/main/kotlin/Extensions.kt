import org.gradle.kotlin.dsl.DependencyHandlerScope

fun DependencyHandlerScope.implementation(dependencyNotation: String) {
    add("implementation", dependencyNotation)
}