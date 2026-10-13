plugins {
    alias(libs.plugins.montpellient.android.feature)
}

android {
    namespace = "fr.montpellient.feature.settings"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}

