package com.dmjgroup.maintainai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dmjgroup.maintainai.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private val Context.settingsDataStore by preferencesDataStore("settings")
private val SERVER_URL = stringPreferencesKey("server_url")
private val SUPABASE_URL_PREF = stringPreferencesKey("supabase_url")
private val SUPABASE_KEY_PREF = stringPreferencesKey("supabase_publishable_key")

val DEFAULT_SERVER_URL: String = BuildConfig.MAINTAIN_API_URL.let { if (it.endsWith("/")) it else "$it/" }
const val APPLICATION_ID = "android"

class SettingsRepository(private val context: Context) {
    val serverUrl: Flow<String> = context.settingsDataStore.data.map { it[SERVER_URL] ?: DEFAULT_SERVER_URL }

    suspend fun setSupabaseConfig(url: String, key: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[SUPABASE_URL_PREF] = url.trim().trimEnd('/')
            prefs[SUPABASE_KEY_PREF] = key.trim()
        }
    }

    suspend fun getSupabaseConfig(): Pair<String, String> {
        return context.settingsDataStore.data.map { prefs ->
            (prefs[SUPABASE_URL_PREF] ?: BuildConfig.SUPABASE_URL) to
                (prefs[SUPABASE_KEY_PREF] ?: BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        }.let { flow ->
            kotlinx.coroutines.flow.first(flow)
        }
    }

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

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val requestUrl = chain.request().url.toString()
                val supabaseConfig = context?.getSharedPreferences("maintain_auth", Context.MODE_PRIVATE)
                val configuredSupabaseUrl = supabaseConfig?.getString("supabase_url", null)?.trimEnd('/')
                    ?: BuildConfig.SUPABASE_URL.trimEnd('/')
                val configuredSupabaseKey = supabaseConfig?.getString("supabase_publishable_key", null)
                    ?: BuildConfig.SUPABASE_PUBLISHABLE_KEY
                val isSupabase = configuredSupabaseUrl.isNotBlank() &&
                    requestUrl.startsWith(configuredSupabaseUrl + "/")

                // Read the token for every request, not when the Retrofit client
                // is created. This is important immediately after Supabase login
                // and after a token refresh.
                val token = context
                    ?.getSharedPreferences("maintain_auth", Context.MODE_PRIVATE)
                    ?.getString("token", null)

                val request = chain.request().newBuilder().apply {
                    if (isSupabase && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
                        header("apikey", configuredSupabaseKey)
                    }

                    if (!isSupabase) {
                        header("X-Maintain-Application", APPLICATION_ID)
                        if (!token.isNullOrBlank()) {
                            header("Authorization", "Bearer $token")
                        }
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

    suspend fun publicSupabaseConfig(baseUrl: String): SupabasePublicConfigResponse =
        api(baseUrl).publicSupabaseConfig()

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

        return DashboardData(
            machines,
            alerts,
            workOrders,
            aiInsights,
            modelStatus
        )
    }

    suspend fun readings(baseUrl: String, machineId: Int): List<SensorReading> =
        api(baseUrl).getReadings(machineId)

    suspend fun alerts(baseUrl: String): List<Alert> =
        api(baseUrl).getAlerts()

    suspend fun modelStatus(baseUrl: String): ModelStatus =
        api(baseUrl).getModelStatus()

    suspend fun riskPredictions(baseUrl: String): RiskPredictionsResponse =
        api(baseUrl).getRiskPredictions()

    suspend fun trainModel(baseUrl: String): TrainModelResponse =
        api(baseUrl).trainModel()
}

class AuthRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("maintain_auth", Context.MODE_PRIVATE)

    fun token(): String? = prefs.getString("token", null)
    fun refreshToken(): String? = prefs.getString("refresh_token", null)

    fun save(token: String, refreshToken: String? = null) {
        prefs.edit().apply {
            putString("token", token)
            if (!refreshToken.isNullOrBlank()) putString("refresh_token", refreshToken)
            apply()
        }
    }

    fun clear() {
        prefs.edit().remove("token").remove("refresh_token").apply()
    }

    private suspend fun ensureSupabaseConfig(): Pair<String, String> {
        val authPrefs = context.getSharedPreferences("maintain_auth", Context.MODE_PRIVATE)
        val cachedUrl = authPrefs.getString("supabase_url", null)?.trim()?.trimEnd('/')
        val cachedKey = authPrefs.getString("supabase_publishable_key", null)?.trim()
        if (!cachedUrl.isNullOrBlank() && !cachedKey.isNullOrBlank()) return cachedUrl to cachedKey

        val config = MaintainRepository(context).publicSupabaseConfig(DEFAULT_SERVER_URL)
        require(config.supabase_url.isNotBlank() && config.supabase_publishable_key.isNotBlank()) {
            "AUTHENTICATION: The backend did not return valid Supabase configuration."
        }
        authPrefs.edit()
            .putString("supabase_url", config.supabase_url.trim().trimEnd('/'))
            .putString("supabase_publishable_key", config.supabase_publishable_key.trim())
            .apply()
        return config.supabase_url.trim().trimEnd('/') to config.supabase_publishable_key.trim()
    }

    private suspend fun refreshAccessToken(): Boolean {
        val refresh = refreshToken() ?: return false
        val (supabaseUrl, _) = ensureSupabaseConfig()
        return runCatching {
            val response = MaintainRepository(context)
                .authApi(supabaseUrl)
                .supabaseRefresh(SupabaseRefreshRequest(refresh))
            val nextToken = response.access_token ?: return@runCatching false
            save(nextToken, response.refresh_token ?: refresh)
            true
        }.getOrDefault(false)
    }

    private fun requireMaintainUser(me: AuthMeResponse): AuthMeResponse {
        require(me.organization_id != null && !me.username.isNullOrBlank()) {
            "SESSION: The authenticated Supabase account is not linked to a Maintain.ai organization."
        }
        return me
    }

    suspend fun session(): Result<AuthMeResponse> {
        return runCatching {
            if (refreshToken() != null) refreshAccessToken()

            if (token().isNullOrBlank()) {
                throw IllegalStateException("SESSION: No Supabase session is stored on this device.")
            }

            val backendApi = MaintainRepository(context).authApi(DEFAULT_SERVER_URL)

            val sync = try {
                backendApi.syncSupabase()
            } catch (first: Throwable) {
                if (!refreshAccessToken()) {
                    throw IllegalStateException(
                        "AUTHORIZATION: Maintain.ai session synchronization failed. " + httpDetail(first),
                        first
                    )
                }
                MaintainRepository(context).authApi(DEFAULT_SERVER_URL).syncSupabase()
            }

            if (sync["needs_onboarding"] == true) {
                throw IllegalStateException(
                    "AUTHORIZATION: This Supabase account is not linked to a Maintain.ai account yet."
                )
            }

            requireMaintainUser(
                MaintainRepository(context).authApi(DEFAULT_SERVER_URL).me()
            )
        }.recoverCatching { first ->
            clear()
            throw first
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
            return Result.failure(IllegalStateException("AUTHENTICATION: Supabase configuration is missing from this Android build."))
        }

        return runCatching {
            val (supabaseUrl, _) = ensureSupabaseConfig()
            val response = try {
                withTimeout(15_000L) {
                    MaintainRepository(context).authApi(supabaseUrl)
                        .supabaseLogin(SupabaseLoginRequest(email.trim(), password))
                }
            } catch (t: Throwable) {
                val detail = if (t is kotlinx.coroutines.TimeoutCancellationException) {
                    "Supabase did not respond within 15 seconds."
                } else {
                    httpDetail(t)
                }
                throw IllegalStateException("AUTHENTICATION: Supabase sign-in failed. " + detail, t)
            }

            val accessToken = response.access_token
                ?: throw IllegalStateException("AUTHENTICATION: Supabase returned no access token.")

            save(accessToken, response.refresh_token)

            val backendApi = MaintainRepository(context).authApi(DEFAULT_SERVER_URL)

            val sync = try {
                backendApi.syncSupabase()
            } catch (t: Throwable) {
                clear()
                throw IllegalStateException(
                    "AUTHORIZATION: Maintain.ai application synchronization failed. " + httpDetail(t),
                    t
                )
            }

            if (sync["needs_onboarding"] == true) {
                clear()
                throw IllegalStateException(
                    "AUTHORIZATION: This Supabase account is not linked to a Maintain.ai account yet."
                )
            }

            val me = try {
                MaintainRepository(context).authApi(DEFAULT_SERVER_URL).me()
            } catch (t: Throwable) {
                clear()
                throw IllegalStateException(
                    "SESSION: Maintain.ai could not load the authenticated user. " + httpDetail(t),
                    t
                )
            }

            requireMaintainUser(me)
        }
    }

    private fun httpDetail(t: Throwable): String {
        val http = t as? retrofit2.HttpException
            ?: return (t.message ?: "Unknown error").trim()
        val raw = runCatching {
            http.response()?.errorBody()?.string().orEmpty()
        }.getOrDefault("")
        val detail = runCatching {
            org.json.JSONObject(raw).optString("detail").takeIf { it.isNotBlank() }
        }.getOrNull()
        return when {
            !detail.isNullOrBlank() -> "HTTP " + http.code() + ": " + detail
            raw.isNotBlank() -> "HTTP " + http.code() + ": " + raw
            else -> "HTTP " + http.code() + ": " + http.message()
        }
    }
}
