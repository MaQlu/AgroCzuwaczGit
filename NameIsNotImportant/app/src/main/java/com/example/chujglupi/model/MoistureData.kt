package com.example.chujglupi.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MoistureData(
        @Json(name = "temperature") val temperature: Float,
        @Json(name = "airHumidity") val airHumidity: Float,
        @Json(name = "soilMoisture") val soilMoisture: Int,
        @Json(name = "lightLevel") val lightLevel: Int,
        @Json(name = "desiredMoisture") val desiredMoisture: Int,
        @Json(name = "autoWatering") val autoWatering: Boolean,
        @Json(name = "fullDate") val fullDate: String
)