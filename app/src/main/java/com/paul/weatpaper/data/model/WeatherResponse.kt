package com.paul.weatpaper.data.model


import com.google.gson.annotations.SerializedName

// Główna klasa odpowiedzi
data class WeatherResponse(
    // Lista obiektów pogodowych (zwykle zawiera jeden element)
    @SerializedName("weather") // Używamy SerializedName, aby powiązać z kluczem JSON
    val weather: List<WeatherInfo>?, // Lista, bo JSON ma tablicę `weather`

    // Obiekt systemowy zawierający wschód/zachód słońca
    @SerializedName("sys")
    val sys: SysInfo?
    // Możesz tu dodać inne pola główne, jeśli będziesz ich potrzebować w przyszłości
    // np. @SerializedName("name") val cityName: String?
)

// Klasa dla elementu w tablicy `weather`
data class WeatherInfo(
    // Główny opis pogody (np. "Clouds", "Rain", "Clear")
    @SerializedName("main")
    val main: String?, // Używamy String? dla bezpieczeństwa (nullable)

    // <<< TUTAJ DODAJ NOWE POLE >>>
    // Szczegółowy opis pogody (np. "few clouds", "overcast clouds", "light rain")
    @SerializedName("description")
    val description: String?, // Używamy String? dla bezpieczeństwa (nullable)

    // Możesz tu dodać inne pola z obiektu `weather`, np. icon
    // @SerializedName("icon") val icon: String?
)

// Klasa dla obiektu `sys`
data class SysInfo(
    // Czas wschodu słońca (Unix timestamp, UTC)
    @SerializedName("sunrise")
    val sunrise: Long?, // Używamy Long? dla bezpieczeństwa

    // Czas zachodu słońca (Unix timestamp, UTC)
    @SerializedName("sunset")
    val sunset: Long? // Używamy Long? dla bezpieczeństwa
    // Możesz tu dodać inne pola z obiektu `sys`, np. country
)