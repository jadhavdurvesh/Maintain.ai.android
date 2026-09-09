package com.dmjgroup.maintainai

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dmjgroup.maintainai.data.MaintainRepository
import com.dmjgroup.maintainai.data.SettingsRepository
import kotlinx.coroutines.flow.first

class AlertWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return runCatching {
            val url = SettingsRepository(applicationContext).serverUrl.first()
            val alerts = MaintainRepository().alerts(url)
            val prefs = applicationContext.getSharedPreferences("alert_notifications", Context.MODE_PRIVATE)
            val lastId = prefs.getInt("last_alert_id", 0)

            alerts.filter { it.id > lastId && it.severity?.lowercase() in setOf("critical", "high") }
                .sortedBy { it.id }
                .forEach { alert ->
                    val notification = NotificationCompat.Builder(applicationContext, "alerts")
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle(alert.title ?: "MAINTAIN AI Alert")
                        .setContentText(alert.message ?: "A maintenance alert requires attention.")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                    applicationContext
                        .getSystemService(NotificationManager::class.java)
                        .notify(alert.id, notification)
                }

            val newestId = alerts.maxOfOrNull { it.id } ?: lastId
            if (newestId > lastId) prefs.edit().putInt("last_alert_id", newestId).apply()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
