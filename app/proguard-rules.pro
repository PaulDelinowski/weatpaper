# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Reguły dla Retrofit + Gson + Coroutines (Kluczowe dla ClassCastException) ---

# Zachowaj atrybuty potrzebne do serializacji/deserializacji i metadanych
# KotlinGeneratedByMember i inne są ważne dla Coroutines i funkcji suspend
-keepattributes Signature, InnerClasses, EnclosingMethod, Modifiers, Exceptions, Module*, *Annotation*, KotlinGeneratedByMember

# Reguły dla Gson
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Zachowaj klasy modelu danych (Twoja reguła była poprawna, powtórzona dla pewności)
# Użycie @SerializedName jest kluczowe, ale zachowanie klasy jest bezpieczniejsze.
-keep class com.paul.weatpaper.data.model.** { *; }
# Dodatkowo zachowaj członków klas modelu (pola, metody), co jest bezpieczniejsze
-keepclassmembers class com.paul.weatpaper.data.model.** { *; }

# Reguły dla Retrofit
# Zachowaj swój interfejs API
-keep interface com.paul.weatpaper.data.remote.WeatherApi { *; }
# Zachowaj klasę Response Retrofita
-keep class retrofit2.Response { *; }

# Reguły dla OkHttp i Okio (zależności Retrofit)
# Zapewniają, że klasy używane do komunikacji sieciowej nie zostaną usunięte/zmienione
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# Reguły dla Kotlin Coroutines (używane z `suspend fun` w Retrofit)
# Zapewniają, że mechanizmy coroutines działają poprawnie po optymalizacji R8
-keepclassmembers class kotlinx.coroutines.flow.** { *; }
-keepclassmembers class **$*COROUTINE* { *; }
-keep class kotlinx.coroutines.flow.StateFlow* { *; }
-keep class kotlin.coroutines.Continuation { *; }


# --- Reguły dla WorkManagera (Twoje reguły były poprawne) ---
-keep public class * extends androidx.work.ListenableWorker
-keepclassmembers public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Dodaj tutaj inne potrzebne reguły specyficzne dla Twojego projektu, jeśli takie istnieją ---

# Przykład dla Glide (często niepotrzebne z procesorem adnotacji KAPT, ale na wszelki wypadek):
# -keep public class * implements com.bumptech.glide.module.GlideModule
# -keep public class * extends com.bumptech.glide.module.AppGlideModule
# -keep public enum com.bumptech.glide.load.ImageHeaderParser$ImageType { *; }
# -keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$ImageType { *; }

