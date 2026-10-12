plugins {
    alias(libs.plugins.montpellient.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.montpellient.connector.mail"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:security"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
