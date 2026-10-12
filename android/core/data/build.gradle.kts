plugins {
    alias(libs.plugins.montpellient.android.library)
}

android {
    namespace = "fr.montpellient.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":shared"))
    api(project(":core:security"))

    api(project(":core:database"))
    api(project(":connector:cas"))
    api(project(":connector:ade"))
    api(project(":connector:moodle"))
    api(project(":connector:mail"))
    api(project(":connector:grades"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
}
