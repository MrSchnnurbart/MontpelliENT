pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "MontpelliENT"

/** Declares a Gradle project whose sources live in [dir] (keeps project paths short). */
fun module(path: String, dir: String) {
    include(path)
    project(path).projectDir = file(dir)
}

// Code partagé appli <-> serveur
module(":shared", "shared")
module(":server", "server")

// Application Android
module(":app", "android/app")
module(":core:model", "android/core/model")
module(":core:designsystem", "android/core/designsystem")
module(":core:data", "android/core/data")
module(":core:security", "android/core/security")
module(":core:database", "android/core/database")

module(":connector:cas", "android/connector/cas")
module(":connector:ade", "android/connector/ade")
module(":connector:moodle", "android/connector/moodle")
module(":connector:mail", "android/connector/mail")
module(":connector:grades", "android/connector/grades")

listOf("onboarding", "home", "timetable", "courses", "grades", "mail", "crous", "campus", "settings")
    .forEach { module(":feature:$it", "android/feature/$it") }

// Projets parents implicites : Gradle 9 exige un répertoire existant
project(":core").projectDir = file("android/core")
project(":connector").projectDir = file("android/connector")
project(":feature").projectDir = file("android/feature")
