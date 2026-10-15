import java.io.FileInputStream
import java.util.Properties

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

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                FileInputStream(keystorePropertiesFile).use { stream ->
                    keystoreProperties.load(stream)
                }
            }

            val storeFilePath = System.getenv("RELEASE_KEYSTORE_FILE")
                ?: keystoreProperties.getProperty("storeFile")
                ?: "keystore/release.jks"

            val storePasswordProp: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                ?: keystoreProperties.getProperty("storePassword")

            val keyAliasProp: String = System.getenv("RELEASE_KEY_ALIAS")
                ?: keystoreProperties.getProperty("keyAlias")
                ?: "montpellient"

            val keyPasswordProp: String? = System.getenv("RELEASE_KEY_PASSWORD")
                ?: keystoreProperties.getProperty("keyPassword")
                ?: storePasswordProp

            val resolvedKeystoreFile = rootProject.file(storeFilePath).takeIf { it.exists() }
                ?: file(storeFilePath).takeIf { it.exists() }

            if (resolvedKeystoreFile != null && !storePasswordProp.isNullOrBlank()) {
                storeFile = resolvedKeystoreFile
                storePassword = storePasswordProp
                keyAlias = keyAliasProp
                keyPassword = keyPasswordProp ?: storePasswordProp
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
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
