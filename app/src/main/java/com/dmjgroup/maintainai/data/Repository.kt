package com.dmjgroup.maintainai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dmjgroup.maintainai.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private val Context.settingsDataStore by preferencesDataStore("settings")
private val SERVER_URL = stringPreferencesKey("server_url")

val DEFAULT_SERVER_URL: String = BuildConfig.MAINTAIN_API_URL.let { if (it.endsWith("/")) it else "$it/" }
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
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
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

    /**
     * Restores the same session contract used by the Workforce client:
     * token -> optional refresh -> backend application sync -> /me.
     *
     * The Android application context remains "android"; the authentication
     * authority is still Supabase and Maintain.ai remains the authorization
     * authority.
     */
    suspend fun session(): Result<AuthMeResponse> {
        val backendApi = MaintainRepository(context).authApi(DEFAULT_SERVER_URL)
        return runCatching {
            // Match the proven Workforce restore flow: an existing Supabase
            // session is already linked to the Maintain.ai user, so restore
            // the authoritative backend session with /me first. Do not call
            // /supabase/sync on every app launch; that endpoint is for initial
            // identity linking/access provisioning and adds an unnecessary
            // network dependency to normal session restoration.
            val me = backendApi.me()
            require(me.organization_id != null && !me.username.isNullOrBlank()) {
                "The Supabase account is not linked to a MAINTAIN AI organization."
            }
            me
        }.recoverCatching { first ->
            if (!refreshAccessToken()) throw first
            val refreshedApi = MaintainRepository(context).authApi(DEFAULT_SERVER_URL)
            val me = refreshedApi.me()
            require(me.organization_id != null && !me.username.isNullOrBlank()) {
                "The Supabase account is not linked to a MAINTAIN AI organization."
            }
            me
        }
    }

    suspend fun changePassword(newPassword: String): Result<Unit> {
        val backendApi = MaintainRepository(context).authApi(DEFAULT_SERVER_URL)
        return runCatching {
            backendApi.changePassword(mapOf("new_password" to newPassword))
            Unit
        }
    }

    suspend fun login(email: String, password: String): Result<AuthMeResponse> {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) {
            return Result.failure(IllegalStateException("Supabase configuration is missing from this Android build."))
        }

        return runCatching {
            val supabaseApi = MaintainRepository(context).authApi(BuildConfig.SUPABASE_URL)

            val response = try {
                supabaseApi.supabaseLogin(SupabaseLoginRequest(email, password))
            } catch (t: Throwable) {
                throw IllegalStateException("AUTHENTICATION: Supabase sign-in failed. ${httpDetail(t)}", t)
            }

            val accessToken = response.access_token
                ?: throw IllegalStateException("AUTHENTICATION: Supabase did not return an access token.")

            save(accessToken, response.refresh_token)

            // Existing Maintain.ai users should be able to enter directly
            // through the authoritative /me endpoint. Sync is only a fallback
            // for accounts that still need their Supabase identity linked.
            val backendApi = MaintainRepository(context).authApi(DEFAULT_SERVER_URL)

            try {
                return@runCatching backendApi.me().also { me ->
                    require(me.organization_id != null && !me.username.isNullOrBlank()) {
                        "SESSION: The Supabase account is not linked to a MAINTAIN AI organization."
                    }
                }
            } catch (first: Throwable) {
                val syncResult = try {
                    backendApi.syncSupabase()
                } catch (sync: Throwable) {
                    clear()
                    throw IllegalStateException(
                        "AUTHORIZATION: Maintain.ai application access failed. " +
                            "Initial session: ${httpDetail(first)}; sync: ${httpDetail(sync)}",
                        sync
                    )
                }

                val needsOnboarding = syncResult["needs_onboarding"] == true
                if (needsOnboarding) {
                    clear()
                    throw IllegalStateException(
                        "AUTHORIZATION: This Supabase account is not linked to a Maintain.ai account yet."
                    )
                }

                try {
                    backendApi.me().also { me ->
                        require(me.organization_id != null && !me.username.isNullOrBlank()) {
                            "SESSION: The Supabase account is not linked to a MAINTAIN AI organization."
                        }
                    }
                } catch (second: Throwable) {
                    clear()
                    throw IllegalStateException(
                        "SESSION: Maintain.ai could not restore your account. " +
                            "Initial session: ${httpDetail(first)}; after sync: ${httpDetail(second)}",
                        second
                    )
                }
            }
        }
    }

    private fun httpDetail(t: Throwable): String {
        val http = t as? retrofit2.HttpException ?: return (t.message ?: "Unknown error").trim()
        val raw = runCatching { http.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
        val detail = runCatching {
            org.json.JSONObject(raw).optString("detail").takeIf { it.isNotBlank() }
        }.getOrNull()
        return when {
            !detail.isNullOrBlank() -> "HTTP ${http.code()}: $detail"
            raw.isNotBlank() -> "HTTP ${http.code()}: $raw"
            else -> "HTTP ${http.code()}: ${http.message()}"
        }
    }
}
