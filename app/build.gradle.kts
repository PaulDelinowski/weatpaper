plugins {
    // Bez podawania wersji – wersje wczytane z settings.gradle (pluginManagement)
    id("com.android.application")
    id("org.jetbrains.kotlin.android")

    // Zamiast "kotlin-kapt" najlepiej użyć oficjalnego ID:
    // id("org.jetbrains.kotlin.kapt")
    // Jeśli jednak "kotlin-kapt" działa Ci poprawnie – możesz pozostać przy nim,
    // ale pamiętaj o prawidłowej definicji w settings.gradle.
    id("kotlin-kapt")

    // Secrets Gradle Plugin
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "com.paul.weatpaper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.paul.weatpaper"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Klucz API – ładowany z gradle property
        buildConfigField(
            "String",
            "OPENWEATHER_API_KEY",
            "\"${providers.gradleProperty("OPENWEATHER_API_KEY").orNull}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val retrofitVersion = "2.9.0"
    val glideVersion = "4.12.0"
    val workRuntimeVersion = "2.8.0"

    implementation("androidx.preference:preference:1.1.1")
    implementation("androidx.work:work-runtime-ktx:$workRuntimeVersion")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("org.json:json:20211205")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.activity:activity-ktx:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")

    // Glide
    implementation("com.github.bumptech.glide:glide:$glideVersion")
    kapt("com.github.bumptech.glide:compiler:$glideVersion")

    // Testy
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
}
