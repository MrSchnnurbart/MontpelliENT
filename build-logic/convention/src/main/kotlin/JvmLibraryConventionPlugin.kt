import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Bibliothèque Kotlin/JVM pure (modèles, code partagé avec le serveur). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.toVersion(Sdk.JAVA_VERSION)
            targetCompatibility = JavaVersion.toVersion(Sdk.JAVA_VERSION)
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(Sdk.JAVA_VERSION.toString()))
            }
        }
        dependencies {
            add("testImplementation", libs.lib("junit"))
        }
    }
}
