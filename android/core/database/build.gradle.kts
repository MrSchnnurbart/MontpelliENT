plugins {
    alias(libs.plugins.montpellient.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.montpellient.core.database"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:security"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
