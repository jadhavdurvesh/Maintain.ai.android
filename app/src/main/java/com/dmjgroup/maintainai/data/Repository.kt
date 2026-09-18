package com.dmjgroup.maintainai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private val Context.settingsDataStore by preferencesDataStore("settings")
private val SERVER_URL = stringPreferencesKey("server_url")

const val DEFAULT_SERVER_URL = "https://maintain-ai-3.vercel.app/"

class SettingsRepository(private val context: Context) {
    val serverUrl: Flow<String> = context.settingsDataStore.data.map { it[SERVER_URL] ?: DEFAULT_SERVER_URL }

    suspend fun setServerUrl(url: String) {
        val normalized = url.trim().ifEmpty { DEFAULT_SERVER_URL }.let {
            if (it.endsWith("/")) it else "$it/"
        }
        context.settingsDataStore.edit { it[SERVER_URL] = normalized }
    }
}

class MaintainRepository(private val context: Context? = null) {
    private fun api(baseUrl: String): MaintainApi {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val token = context?.getSharedPreferences("maintain_auth", Context.MODE_PRIVATE)?.getString("token", null)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    if (baseUrl.startsWith(BuildConfig.SUPABASE_URL) && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                    if (!chain.request().url.encodedPath.contains("/auth/v1/")) header("X-Maintain-Application", "android")
                    if (!chain.request().url.encodedPath.contains("/auth/v1/") && !token.isNullOrBlank()) {
                        header("Authorization", "Bearer $token")
                    }
                }.build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MaintainApi::class.java)
    }

    fun authApi(baseUrl: String): MaintainApi = api(baseUrl)

    suspend fun load(baseUrl: String): DashboardData {
        val api = api(baseUrl)
        val machines = api.getMachines()
        val alerts = runCatching { api.getAlerts() }.getOrDefault(emptyList())
        val workOrders = runCatching { api.getWorkOrders() }.getOrDefault(emptyList())
        val modelStatus = runCatching { api.getModelStatus() }.getOrNull()
        val aiInsights = runCatching {
            val response = api.getRiskPredictions()
            response.predictions.map { prediction ->
                AiModelInsight(
                    machineId = prediction.machineId,
                    machineName = prediction.machineName,
                    actualHealthScore = prediction.actualHealthScore,
                    predictedHealthScore = prediction.predictedHealthScore,
                    riskLevel = prediction.riskLevel,
                    reason = prediction.reason,
                    modelVersion = response.modelVersion,
                    trainedAt = response.trainedAt
                )
            }
        }.getOrDefault(emptyList())
        return DashboardData(machines, alerts, workOrders, aiInsights, modelStatus)
    }

    suspend fun readings(baseUrl: String, machineId: Int): List<SensorReading> = api(baseUrl).getReadings(machineId)
    suspend fun alerts(baseUrl: String): List<Alert> = api(baseUrl).getAlerts()
    suspend fun modelStatus(baseUrl: String): ModelStatus = api(baseUrl).getModelStatus()
    suspend fun riskPredictions(baseUrl: String): RiskPredictionsResponse = api(baseUrl).getRiskPredictions()
    suspend fun trainModel(baseUrl: String): TrainModelResponse = api(baseUrl).trainModel()
}


class AuthRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("maintain_auth", Context.MODE_PRIVATE)
    fun token(): String? = prefs.getString("token", null)
    fun save(token: String) { prefs.edit().putString("token", token).apply() }
    fun clear() { prefs.edit().remove("token").apply() }
    suspend fun login(email: String, password: String): Boolean {
        val response = MaintainRepository().authApi(BuildConfig.SUPABASE_URL)
            .supabaseLogin(SupabaseLoginRequest(email, password))
        val token = response.access_token ?: return false
        save(token)
        return true
    }
}
