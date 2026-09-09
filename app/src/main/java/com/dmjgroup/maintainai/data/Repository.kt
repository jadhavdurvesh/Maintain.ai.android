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
        val normalized = if (url.endsWith("/")) url else "$url/"
        context.settingsDataStore.edit { it[SERVER_URL] = normalized }
    }
}

class MaintainRepository {
    private fun api(baseUrl: String): MaintainApi {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MaintainApi::class.java)
    }

    suspend fun load(baseUrl: String): DashboardData {
        val api = api(baseUrl)
        val machines = api.getMachines()
        val alerts = runCatching { api.getAlerts() }.getOrDefault(emptyList())
        val workOrders = runCatching { api.getWorkOrders() }.getOrDefault(emptyList())
        return DashboardData(machines, alerts, workOrders)
    }

    suspend fun readings(baseUrl: String, machineId: Int): List<SensorReading> {
        return api(baseUrl).getReadings(machineId)
    }

    suspend fun alerts(baseUrl: String): List<Alert> = api(baseUrl).getAlerts()
}
