// Fichier de build racine : les plugins sont déclarés ici (apply false) pour être chargés
// une seule fois dans le classloader racine, puis appliqués par les convention plugins.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
