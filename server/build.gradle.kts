plugins {
    alias(libs.plugins.montpellient.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("fr.montpellient.server.ApplicationKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.websockets)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.server.test.host)
}
