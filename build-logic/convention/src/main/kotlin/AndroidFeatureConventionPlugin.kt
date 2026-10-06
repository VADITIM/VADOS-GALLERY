import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

// A feature is a screen built from the core modules; features never depend on each other, only the app joins them.
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("vados.android.library")
        dependencies {
            add("implementation", project(":core:settings"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:design"))
            add("implementation", project(":core:ui"))
        }
    }
}
