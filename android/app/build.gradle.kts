plugins {
    alias(libs.plugins.montpellient.android.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.montpellient.app"

    defaultConfig {
        applicationId = "fr.montpellient.app"
        versionCode = 1
        versionName = "1.0.0"
    }

    androidResources {
        // Langues embarquées : français (défaut) et anglais
        localeFilters += listOf("fr", "en")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Permet d'installer directement l'APK release en le signant par défaut avec le keystore local
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

tasks.register<Copy>("copyReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("*.apk")
        rename { "MontpelliENT-v1.0.0-release.apk" }
    }
    into(rootProject.file("dist"))
}

tasks.register<Copy>("copyDebugApk") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug")) {
        include("*.apk")
        rename { "MontpelliENT-v1.0.0-debug.apk" }
    }
    into(rootProject.file("dist"))
}

afterEvaluate {
    tasks.findByName("assembleRelease")?.finalizedBy("copyReleaseApk")
    tasks.findByName("assembleDebug")?.finalizedBy("copyDebugApk")
}





dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    listOf("onboarding", "home", "timetable", "courses", "grades", "mail", "crous", "campus", "settings")
        .forEach { implementation(project(":feature:$it")) }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
