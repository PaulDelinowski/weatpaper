plugins {
    // Udostępniamy pluginy - wersje pochodzą z settings.gradle
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
