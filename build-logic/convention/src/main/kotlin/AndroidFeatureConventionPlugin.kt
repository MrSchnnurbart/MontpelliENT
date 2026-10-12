import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Module « feature » : bibliothèque Android + Compose + dépendances communes des écrans. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("montpellient.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:data"))

            add("implementation", libs.lib("androidx-compose-material3"))
            add("implementation", libs.lib("androidx-compose-material-icons-extended"))
            add("implementation", libs.lib("androidx-compose-ui"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("kotlinx-coroutines-android"))
        }
    }
}
