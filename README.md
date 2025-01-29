WeatPaper

WeatPaper is an Android application that automatically updates your wallpaper based on the current weather conditions and time of day. It uses the OpenWeather API to fetch weather data and updates the wallpaper accordingly.

Features

Fetches real-time weather data using OpenWeather API.

Changes wallpaper based on weather conditions (e.g., clear, cloudy, rainy, stormy, snowy).

Adjusts wallpaper based on the time of day (morning, day, sunset, night).

Runs periodically in the background using WorkManager.

Installation

Prerequisites

Android Studio installed

OpenWeather API key

Internet and location permissions enabled

Steps

Clone the repository:

git clone https://github.com/yourusername/weatpaper.git

Open the project in Android Studio.

Add your OpenWeather API key to local.properties:

OPENWEATHER_API_KEY=your_api_key_here

Sync the project and build the application.

Install and run the app on your Android device.

Usage

Grant the necessary permissions for location and internet access.

Press "Start" to begin the wallpaper update service.

Press "Stop" to halt the service.

Visit "How It Works" or "Info" sections for additional details.

Project Structure

com.paul.weatpaper
│── data
│   └── remote
│       ├── ApiClient.kt  # Handles API requests
│       ├── WeatherApi.kt  # Defines API endpoints
│── ui
│   ├── MainActivity.kt  # Main UI activity
│── utils
│   ├── WallpaperChanger.kt  # Handles wallpaper updates
│   ├── LocationProvider.kt  # Retrieves device location
│── worker
│   ├── WallpaperWorker.kt  # Background worker for periodic updates

Permissions Required

ACCESS_FINE_LOCATION – For fetching weather data based on location.

INTERNET – To fetch weather data from OpenWeather API.

SET_WALLPAPER – To update the device wallpaper.

Dependencies

Retrofit – API communication

WorkManager – Background task scheduling

Google Play Services Location – Fetching device location

Contributing

Feel free to contribute by opening an issue or submitting a pull request.

License

This project is licensed under the MIT License.

Developed by Paul 🚀
