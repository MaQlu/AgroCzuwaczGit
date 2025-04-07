package com.example.czujnaaplikacja.network

import com.example.czujnaaplikacja.model.AutoWateringRequest
import com.example.czujnaaplikacja.model.MoistureData
import com.example.czujnaaplikacja.model.MoistureRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @GET("data")
    suspend fun getSensorData(): Response<MoistureData>

    @POST("setMoisture")
    suspend fun setDesiredMoisture(@Body request: MoistureRequest): Response<Unit>

    @POST("setAutoWatering")
    suspend fun setAutoWatering(@Body request: AutoWateringRequest): Response<Unit>

    @POST("waterPlant")
    suspend fun waterPlant(): Response<Unit>

    @GET("debugPump")
    suspend fun debugPump(): Response<Unit>

    @POST("/setPumpDuration")
    suspend fun setPumpDuration(@Body request: Map<String, Int>): Response<Void>
}