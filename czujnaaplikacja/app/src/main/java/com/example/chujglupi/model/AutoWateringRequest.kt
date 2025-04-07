package com.example.czujnaaplikacja.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AutoWateringRequest(
    @Json(name = "enabled") val enabled: Boolean
)