package com.dmjgroup.maintainai.data

import retrofit2.http.GET
import retrofit2.http.Path

interface MaintainApi {
    @GET("api/machines") suspend fun getMachines(): List<Machine>
    @GET("api/alerts") suspend fun getAlerts(): List<Alert>
    @GET("api/work-orders") suspend fun getWorkOrders(): List<WorkOrder>
    @GET("api/machines/{id}/readings") suspend fun getReadings(@Path("id") id: Int): List<SensorReading>
    @GET("api/analytics/model-status") suspend fun getModelStatus(): ModelStatus
    @GET("api/analytics/risk-predictions") suspend fun getRiskPredictions(): RiskPredictionsResponse
    @POST("api/analytics/train") suspend fun trainModel(): TrainModelResponse
}
