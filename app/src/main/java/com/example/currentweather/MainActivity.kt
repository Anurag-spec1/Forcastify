package com.example.currentweather

import kotlinx.coroutines.*
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.currentweather.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.location.Location
import android.Manifest
import android.location.Address
import android.location.Geocoder
import android.widget.SearchView
import com.airbnb.lottie.LottieDrawable
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
        } else {
            getCurrentLocation()
        }

        getData(" ")
        setupSearch()
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    getAddressFromLocation(it.latitude, it.longitude)
                } ?: Log.e("Location", "Location is null")
            }
        }
    }

    private fun getAddressFromLocation(lat: Double, lng: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                val addresses: List<Address> = geocoder.getFromLocation(lat, lng, 1) ?: emptyList()

                if (addresses.isNotEmpty()) {
                    val city = addresses[0].locality ?: "Unknown"

                    withContext(Dispatchers.Main) {
                        getData(city)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            Log.e("Permission", "Location permission denied")
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    if (it.isNotBlank()) {
                        getData(it)
                        binding.searchView.clearFocus()
                    } else {
                        Toast.makeText(this@MainActivity, "Please enter a city name", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String?) = false
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
                    response.body()?.let { updateUI(it, cityName) }
                        ?: showError("No weather data available")
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
                weatherConditionText.text = weatherData.current?.condition?.text ?: "N/A"
                temperatureText.text = "${weatherData.current?.temp_c ?: "N/A"}°C"

                humidityValue.text = "${weatherData.current?.humidity ?: "N/A"}%"
                visibilityValue.text = "${weatherData.current?.vis_km ?: "N/A"} km"
                pressureValue.text = "${weatherData.current?.pressure_mb ?: "N/A"} mb"
                windDegreeValue.text = "${weatherData.current?.wind_degree ?: "N/A"}°"
                windSpeedValue.text = "${weatherData.current?.wind_kph ?: "N/A"} km/h"
                windDirectionValue.text = weatherData.current?.wind_dir ?: "N/A"

                dayNameText.text = getDayName(System.currentTimeMillis())
                dateText.text = getCurrentDate()
                cityText.text = cityName

                weatherData.current?.condition?.text?.let { setWeatherIcon(it) }
            }
        } catch (e: Exception) {
            showError("Error updating UI")
            Log.e("WeatherUI", "UI update failed", e)
        }
    }

    private fun setWeatherIcon(conditions: String) {
        val lowerConditions = conditions.lowercase(Locale.getDefault())
        val animationRes= when {
            lowerConditions.contains("clear") || lowerConditions.contains("sunny") -> R.raw.sunny
            lowerConditions.contains("cloud") || lowerConditions.contains("overcast") ||
                    lowerConditions.contains("mist") || lowerConditions.contains("fog") -> R.raw.cloudy
            lowerConditions.contains("rain") || lowerConditions.contains("drizzle") ||
                    lowerConditions.contains("shower") -> R.raw.rainy
            lowerConditions.contains("snow") || lowerConditions.contains("blizzard") -> R.raw.frost
            else -> R.raw.sunny
        }

        binding.lottieAnimation.apply {
            setAnimation(animationRes)
            repeatCount = LottieDrawable.INFINITE
            playAnimation()
        }

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