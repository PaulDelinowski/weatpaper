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

# --- Reguły Specyficzne dla Aplikacji Weatpaper ---

# Zachowaj (keep) klasy modelu danych z pakietu com.paul.weatpaper.data.model
# przed usunięciem lub zmianą nazw przez R8/ProGuard.
# Jest to kluczowe dla poprawnego działania bibliotek takich jak Gson (używanej przez Retrofit),
# które polegają na nazwach pól do mapowania danych JSON.
# `**` oznacza wszystkie klasy w tym pakiecie i jego podpakietach.
# `{ *; }` oznacza zachowanie wszystkich pól i metod w tych klasach.
-keep class com.paul.weatpaper.data.model.** { *; }

# Zachowaj atrybuty 'Signature'. Jest to ważne dla bibliotek (jak Gson),
# które mogą potrzebować informacji o typach generycznych podczas działania
# (np. przy deserializacji list List<WeatherInfo>).
-keepattributes Signature

# Zachowaj adnotacje. Jest to ważne, ponieważ Gson używa adnotacji
# takich jak @SerializedName do poprawnego mapowania pól JSON na pola klas Kotlin/Java.
# Inne biblioteki również mogą polegać na adnotacjach.
-keepattributes *Annotation*

# --- Koniec reguł dla Weatpaper ---