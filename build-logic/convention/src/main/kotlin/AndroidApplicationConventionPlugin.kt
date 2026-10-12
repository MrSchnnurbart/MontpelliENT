import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Application Android + Compose (Kotlin intégré à AGP 9). */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<ApplicationExtension> {
            compileSdk = libs.version("compileSdk").toInt()
            defaultConfig {
                minSdk = libs.version("minSdk").toInt()
                targetSdk = libs.version("targetSdk").toInt()
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.toVersion(Sdk.JAVA_VERSION)
                targetCompatibility = JavaVersion.toVersion(Sdk.JAVA_VERSION)
            }
            buildFeatures {
                compose = true
            }
            packaging {
                resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
        dependencies {
            add("implementation", platform(libs.lib("androidx-compose-bom")))
            add("androidTestImplementation", platform(libs.lib("androidx-compose-bom")))
        }
    }
}
