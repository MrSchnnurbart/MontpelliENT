plugins {
    alias(libs.plugins.montpellient.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.montpellient.connector.grades"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:security"))
    api(project(":connector:cas"))
    api(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
