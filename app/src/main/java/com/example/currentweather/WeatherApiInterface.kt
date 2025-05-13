package com.example.currentweather

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

    //https://api.weatherapi.com/v1/current.json?key=de64d5c5447c429f8aa64933253101&q=Patna
    interface WeatherApiInterface {
        @GET("current.json")
        fun getData(
            @Query("q") cityName: String,
            @Query("key") apiKey: String = "de64d5c5447c429f8aa64933253101", // Just the key, no URL parts
            @Query("aqi") aqi: String = "no"
        ): Call<AnuragWeather>
    }

    object RetrofitWeather {
        private const val BASE_URL = "https://api.weatherapi.com/v1/"

        val apiInterface: WeatherApiInterface by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WeatherApiInterface::class.java)
        }
    }