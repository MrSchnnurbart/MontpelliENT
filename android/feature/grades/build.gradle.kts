plugins {
    alias(libs.plugins.montpellient.android.feature)
}

android {
    namespace = "fr.montpellient.feature.grades"
}

dependencies {
    implementation(project(":connector:grades"))
}

