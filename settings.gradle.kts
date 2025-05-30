pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.4.0"
        id("org.jetbrains.kotlin.android") version "1.9.22"
        id("org.jetbrains.kotlin.kapt") version "1.9.22"
        id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1"
        id ("com.google.gms.google-services") version "4.4.1" apply false // Sprawdź najnowszą wersję
        id ("com.google.firebase.crashlytics") version "2.9.9" apply false // Sprawdź najnowszą wersję
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Weatpaper"
include(":app")
