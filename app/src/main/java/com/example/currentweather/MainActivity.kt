package com.example.currentweather

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.widget.SearchView.OnQueryTextListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.currentweather.databinding.ActivityMainBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize with default city
        getData("Patna")
        setupSearch()
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    if (it.isNotBlank()) {
                        getData(it)
                        binding.searchView.clearFocus() // Hide keyboard after search
                    } else {
                        Toast.makeText(this@MainActivity, "Please enter a city name", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false // Return false to not handle text changes
            }
        })
    }

    private fun getData(cityName: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        RetrofitWeather.apiInterface.getData(cityName).enqueue(object : Callback<AnuragWeather> {
            override fun onResponse(call: Call<AnuragWeather>, response: Response<AnuragWeather>) {
                showLoading(false)

                if (response.isSuccessful) {
                    response.body()?.let { weatherData ->
                        updateUI(weatherData, cityName)
                    } ?: run {
                        showError("No weather data available")
                    }
                } else {
                    val errorMessage = when (response.code()) {
                        404 -> "City not found"
                        401 -> "Invalid API key"
                        429 -> "Too many requests"
                        else -> "Error: ${response.code()}"
                    }
                    showError(errorMessage)
                }
            }

            override fun onFailure(call: Call<AnuragWeather>, t: Throwable) {
                showLoading(false)
                val errorMessage = when (t) {
                    is SocketTimeoutException -> "Request timeout"
                    is ConnectException -> "No internet connection"
                    else -> "Error: ${t.localizedMessage}"
                }
                showError(errorMessage)
                Log.e("WeatherAPI", "API call failed", t)
            }
        })
    }

    private fun updateUI(weatherData: AnuragWeather, cityName: String) {
        try {
            binding.apply {
                // Current weather
                weatherConditionText.text = weatherData.current?.condition?.text ?: "N/A"
                temperatureText.text = "${weatherData.current?.temp_c ?: "N/A"}°C"

                // Weather details
                humidityValue.text = "${weatherData.current?.humidity ?: "N/A"} %"
                visibilityValue.text = "${weatherData.current?.vis_km ?: "N/A"} km"
                pressureValue.text = "${weatherData.current?.pressure_mb ?: "N/A"} mb"
                windDegreeValue.text = "${weatherData.current?.wind_degree ?: "N/A"}°"
                windSpeedValue.text = "${weatherData.current?.wind_kph ?: "N/A"} km/h"
                windDirectionValue.text = weatherData.current?.wind_dir ?: "N/A"

                // Date and location
                dayNameText.text = getDayName(System.currentTimeMillis())
                dateText.text = getCurrentDate()
                cityText.text = cityName

                // Update weather icon
                weatherData.current?.condition?.text?.let { condition ->
                    setWeatherIcon(condition)
                }
            }
        } catch (e: Exception) {
            showError("Error updating UI")
            Log.e("WeatherUI", "UI update failed", e)
        }
    }

    private fun setWeatherIcon(conditions: String) {
        val lowerConditions = conditions.lowercase(Locale.getDefault())
        binding.weatherIcon.setImageResource(
            when {
                lowerConditions.contains("clear") || lowerConditions.contains("sunny") -> R.drawable.weather
                lowerConditions.contains("cloud") || lowerConditions.contains("overcast") ||
                        lowerConditions.contains("mist") || lowerConditions.contains("fog") -> R.drawable.cloudy
                lowerConditions.contains("rain") || lowerConditions.contains("drizzle") ||
                        lowerConditions.contains("shower") -> R.drawable.rainy
                lowerConditions.contains("snow") || lowerConditions.contains("blizzard") -> R.drawable.frost
                else -> R.drawable.weather
            }
        )
    }

    private fun getDayName(timestamp: Long): String {
        return try {
            SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
        } catch (e: Exception) {
            Log.e("DateFormat", "Error formatting day", e)
            "N/A"
        }
    }

    private fun getCurrentDate(): String {
        return try {
            SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
        } catch (e: Exception) {
            Log.e("DateFormat", "Error formatting date", e)
            "N/A"
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
    }

    private fun showLoading(show: Boolean) {

    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}