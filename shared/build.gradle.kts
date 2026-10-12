plugins {
    alias(libs.plugins.montpellient.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
