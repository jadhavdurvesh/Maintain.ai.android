package com.dmjgroup.maintainai.data

import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

data class SupabaseLoginRequest(val email: String, val password: String)
data class SupabaseRefreshRequest(val refresh_token: String)
data class SupabaseSyncRequest(
    val organization_name: String? = null,
    val username: String? = null,
    val full_name: String? = null,
    val registration_mode: Boolean = false
)
data class SupabaseLoginResponse(val access_token: String?, val refresh_token: String?, val user: Map<String, Any>?)
data class AuthMeResponse(val user_id: Int?, val username: String?, val role: String?, val organization_id: Int?, val organization_name: String?, val password_change_required: Boolean? = false)
data class RealtimeTokenResponse(val access_token: String, val expires_in: Int = 3300)

interface MaintainApi {
    @retrofit2.http.POST("api/auth/supabase/sync") suspend fun syncSupabase(@Body payload: SupabaseSyncRequest = SupabaseSyncRequest()): Map<String, Any?>
    @GET("api/auth/me") suspend fun me(): AuthMeResponse
    @retrofit2.http.POST("api/auth/realtime-token") suspend fun realtimeToken(): RealtimeTokenResponse
    @retrofit2.http.POST("api/auth/password-change") suspend fun changePassword(@Body request: Map<String, String>): Map<String, Any?>
    @retrofit2.http.POST("auth/v1/token?grant_type=password")
    suspend fun supabaseLogin(@Body request: SupabaseLoginRequest): SupabaseLoginResponse
    @retrofit2.http.POST("auth/v1/token?grant_type=refresh_token")
    suspend fun supabaseRefresh(@Body request: SupabaseRefreshRequest): SupabaseLoginResponse

    @GET("api/machines") suspend fun getMachines(): List<Machine>
    @GET("api/alerts") suspend fun getAlerts(): List<Alert>
    @GET("api/work-orders") suspend fun getWorkOrders(): List<WorkOrder>
    @GET("api/machines/{id}/readings") suspend fun getReadings(@Path("id") id: Int): List<SensorReading>
    @GET("api/analytics/model-status") suspend fun getModelStatus(): ModelStatus
    @GET("api/analytics/risk-predictions") suspend fun getRiskPredictions(): RiskPredictionsResponse
    @POST("api/analytics/train") suspend fun trainModel(): TrainModelResponse
}
