package com.user.jarvis

data class FlightItem(
    val flightId: String,
    val flight: String,
    val callsign: String,
    val squawk: String,
    val clicks: Int,
    val fromIata: String,
    val fromCity: String,
    val toIata: String,
    val toCity: String,
    val model: String,
    val type: String
)
