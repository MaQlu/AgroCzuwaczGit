package com.example.chujglupi.network

import com.example.chujglupi.model.AutoWateringRequest
import com.example.chujglupi.model.MoistureData
import com.example.chujglupi.model.MoistureRequest
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
}