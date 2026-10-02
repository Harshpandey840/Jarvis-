package com.user.jarvis

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject

object FlightRepository {
    private const val TAG = "FlightRepository"
    var lastFetchedFlights: List<FlightItem> = emptyList()
    var lastUpdateTime: Double = 0.0

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("x-rapidapi-key")
        })
        .build()

    fun fetchFlights(context: Context, onSuccess: (List<FlightItem>) -> Unit, onError: (String) -> Unit) {
        val apiKey = "604db0b7f8msh4f0e6fff94e701ap11fae6jsnbeac31092692"

        Thread {
            try {
                val host = "flightradar243.p.rapidapi.com"
                val url = "https://$host/v1/flights/most-tracked"

                Log.d(TAG, "Request URL: $url")

                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-rapidapi-host", host)
                    .addHeader("x-rapidapi-key", apiKey)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    Log.d(TAG, "Response Code: ${response.code}")
                    Log.d(TAG, "Response Body: $responseBody")

                    if (!response.isSuccessful) {
                        when (response.code) {
                            429 -> onError("API rate limit exceeded. Badh me try karo.")
                            401, 403 -> onError("Invalid API key.")
                            else -> onError("Server unavailable. Code: ${response.code}")
                        }
                        return@use
                    }

                    if (responseBody.isBlank()) {
                        onError("Empty response from server.")
                        return@use
                    }

                    try {
                        val root = JSONObject(responseBody)
                        val status = root.optString("status", "unknown")
                        if (status != "success" && status != "unknown") {
                            onError("API returned error status: $status")
                            return@use
                        }

                        val dataObj = root.optJSONObject("data") ?: JSONObject()
                        val updateTime = dataObj.optDouble("update_time", 0.0)
                        val dataArray = dataObj.optJSONArray("data")

                        val flights = mutableListOf<FlightItem>()
                        if (dataArray != null) {
                            for (i in 0 until dataArray.length()) {
                                val itemObj = dataArray.optJSONObject(i) ?: continue
                                flights.add(
                                    FlightItem(
                                        flightId = itemObj.optString("flight_id", "Unknown"),
                                        flight = itemObj.optString("flight", "Unknown"),
                                        callsign = itemObj.optString("callsign", "Unknown"),
                                        squawk = itemObj.optString("squawk", "Unknown"),
                                        clicks = itemObj.optInt("clicks", 0),
                                        fromIata = itemObj.optString("from_iata", "Unknown"),
                                        fromCity = itemObj.optString("from_city", "Unknown"),
                                        toIata = itemObj.optString("to_iata", "Unknown"),
                                        toCity = itemObj.optString("to_city", "Unknown"),
                                        model = itemObj.optString("model", "Unknown"),
                                        type = itemObj.optString("type", "Unknown")
                                    )
                                )
                            }
                        }

                        if (flights.isEmpty()) {
                            onError("Empty flight list.")
                            return@use
                        }

                        lastFetchedFlights = flights
                        lastUpdateTime = updateTime
                        onSuccess(flights)

                    } catch (e: Exception) {
                        Log.e(TAG, "JSON Parse error", e)
                        onError("Invalid JSON response.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network exception", e)
                onError("No internet connection or network error.")
            }
        }.start()
    }
}
