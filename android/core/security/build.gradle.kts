plugins {
    alias(libs.plugins.montpellient.android.library)
}

android {
    namespace = "fr.montpellient.core.security"
}

dependencies {
    api(libs.androidx.security.crypto)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
}
