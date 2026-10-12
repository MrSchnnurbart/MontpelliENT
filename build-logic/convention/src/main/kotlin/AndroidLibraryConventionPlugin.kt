import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Bibliothèque Android. Compose est activé si le module le demande via
 * `montpellient.android.feature` ou en appliquant le plugin compose lui-même.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            compileSdk = libs.version("compileSdk").toInt()
            defaultConfig {
                minSdk = libs.version("minSdk").toInt()
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.toVersion(Sdk.JAVA_VERSION)
                targetCompatibility = JavaVersion.toVersion(Sdk.JAVA_VERSION)
            }
            testOptions {
                unitTests.isReturnDefaultValues = true
            }
        }

        // Si le module applique le plugin Compose, on active Compose et la BOM.
        pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
            extensions.configure<LibraryExtension> {
                buildFeatures { compose = true }
            }
            dependencies {
                add("implementation", platform(libs.lib("androidx-compose-bom")))
                add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
            }
        }

        dependencies {
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        }
    }
}
