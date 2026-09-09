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

class SettingsRepository(private val context: Context) {
    val serverUrl: Flow<String> = context.settingsDataStore.data.map { it[SERVER_URL] ?: "http://10.0.2.2:8000/" }
    suspend fun setServerUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        context.settingsDataStore.edit { it[SERVER_URL] = normalized }
    }
}

class MaintainRepository {
    suspend fun load(baseUrl: String): DashboardData {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        val api = Retrofit.Builder().baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client).addConverterFactory(GsonConverterFactory.create()).build().create(MaintainApi::class.java)
        val machines = api.getMachines()
        val alerts = runCatching { api.getAlerts() }.getOrDefault(emptyList())
        val workOrders = runCatching { api.getWorkOrders() }.getOrDefault(emptyList())
        return DashboardData(machines, alerts, workOrders)
    }

    suspend fun readings(baseUrl: String, machineId: Int): List<SensorReading> {
        val api = Retrofit.Builder().baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .addConverterFactory(GsonConverterFactory.create()).build().create(MaintainApi::class.java)
        return api.getReadings(machineId)
    }
}
