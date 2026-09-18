package com.dmjgroup.maintainai.data

import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

data class SupabaseLoginRequest(val email: String, val password: String)
data class SupabaseLoginResponse(val access_token: String?, val refresh_token: String?, val user: Map<String, Any>?)

interface MaintainApi {
    @retrofit2.http.POST("auth/v1/token?grant_type=password")
    suspend fun supabaseLogin(@Body request: SupabaseLoginRequest): SupabaseLoginResponse

    @GET("api/machines") suspend fun getMachines(): List<Machine>
    @GET("api/alerts") suspend fun getAlerts(): List<Alert>
    @GET("api/work-orders") suspend fun getWorkOrders(): List<WorkOrder>
    @GET("api/machines/{id}/readings") suspend fun getReadings(@Path("id") id: Int): List<SensorReading>
    @GET("api/analytics/model-status") suspend fun getModelStatus(): ModelStatus
    @GET("api/analytics/risk-predictions") suspend fun getRiskPredictions(): RiskPredictionsResponse
    @POST("api/analytics/train") suspend fun trainModel(): TrainModelResponse
}
