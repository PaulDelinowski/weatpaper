package com.paul.weatpaper.data.remote

// import com.google.gson.JsonObject // Już niepotrzebny
import com.paul.weatpaper.data.model.WeatherResponse // <<< Zaimportuj nową klasę
import retrofit2.Call // Już niepotrzebny
import retrofit2.Response // <<< Potrzebny import dla Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    @GET("data/2.5/weather")
    suspend fun getWeather( // <<< Dodaj suspend
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String
    ): Response<WeatherResponse> // <<< Zmień zwracany typ (Response z naszą klasą)
}