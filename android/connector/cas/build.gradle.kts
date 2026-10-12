plugins {
    alias(libs.plugins.montpellient.android.library)
}

android {
    namespace = "fr.montpellient.connector.cas"
}

dependencies {
    api(project(":core:security"))
    api(project(":core:model"))
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
