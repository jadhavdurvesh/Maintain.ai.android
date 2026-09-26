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

const val DEFAULT_SERVER_URL = BuildConfig.MAINTAIN_API_URL.let { if (it.endsWith("/")) it else "$it/" }
const val APPLICATION_ID = "android"

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
                val requestUrl = chain.request().url.toString()
                val isSupabase = BuildConfig.SUPABASE_URL.isNotBlank() &&
                    requestUrl.startsWith(BuildConfig.SUPABASE_URL.trimEnd('/') + "/")
                val request = chain.request().newBuilder().apply {
                    if (isSupabase && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
                        header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                    }
                    if (!isSupabase) {
                        header("X-Maintain-Application", APPLICATION_ID)
                        if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
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
    fun refreshToken(): String? = prefs.getString("refresh_token", null)

    fun save(token: String, refreshToken: String? = null) {
        prefs.edit().putString("token", token).apply()
        if (!refreshToken.isNullOrBlank()) prefs.edit().putString("refresh_token", refreshToken).apply()
    }

    fun clear() {
        prefs.edit().remove("token").remove("refresh_token").apply()
    }

    private suspend fun refreshAccessToken(): Boolean {
        val refresh = refreshToken() ?: return false
        return runCatching {
            val response = MaintainRepository(context).authApi(BuildConfig.SUPABASE_URL)
                .supabaseRefresh(SupabaseRefreshRequest(refresh))
            val nextToken = response.access_token ?: return@runCatching false
            save(nextToken, response.refresh_token ?: refresh)
            true
        }.getOrDefault(false)
    }

    suspend fun session(): Result<AuthMeResponse> {
        val backendApi = MaintainRepository(context).authApi(DEFAULT_SERVER_URL)
        return runCatching { backendApi.me() }.recoverCatching { first ->
            if (!refreshAccessToken()) throw first
            MaintainRepository(context).authApi(DEFAULT_SERVER_URL).me()
        }
    }

    suspend fun login(email: String, password: String): Result<AuthMeResponse> {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) {
            return Result.failure(IllegalStateException("Supabase configuration is missing from this Android build."))
        }

        val supabaseApi = MaintainRepository(context).authApi(BuildConfig.SUPABASE_URL)
        val response = supabaseApi.supabaseLogin(SupabaseLoginRequest(email, password))
        val accessToken = response.access_token
            ?: return Result.failure(IllegalStateException("Supabase did not return an access token."))

        save(accessToken, response.refresh_token)

        val backendApi = MaintainRepository(context).authApi(DEFAULT_SERVER_URL)
        return runCatching {
            backendApi.syncSupabase()
            val me = backendApi.me()
            require(me.organization_id != null && !me.username.isNullOrBlank()) {
                "The Supabase account is not linked to a MAINTAIN AI organization."
            }
            me
        }
    }
}
