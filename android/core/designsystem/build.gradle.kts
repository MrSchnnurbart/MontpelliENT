plugins {
    alias(libs.plugins.montpellient.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "fr.montpellient.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.ui)
    implementation(libs.materialkolor)
}
