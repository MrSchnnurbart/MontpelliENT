plugins {
    alias(libs.plugins.montpellient.android.library)
}

android {
    namespace = "fr.montpellient.connector.ade"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:security"))
    api(project(":connector:cas"))
    api(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
