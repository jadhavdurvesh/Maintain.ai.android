package com.dmjgroup.maintainai.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LiveTelemetry(
    val machineId: Int,
    val machineName: String?,
    val readingType: String,
    val value: Double,
    val unit: String?,
    val recordedAt: String?
)

class RealtimeTelemetry(
    private val context: android.content.Context,
    private val scope: CoroutineScope,
    private val onReading: (LiveTelemetry) -> Unit,
    private val onStatus: (String) -> Unit = {}
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var stopped = false
    private var attempt = 0

    fun start() {
        stopped = false
        reconnectJob?.cancel()
        connect()
    }

    fun stop() {
        stopped = true
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, "client stopped")
        socket = null
        onStatus("offline")
    }

    private fun connect() {
        if (stopped) return
        scope.launch(Dispatchers.IO) {
            try {
                onStatus("connecting")
                val repo = MaintainRepository(context)
                val base = repo.authApi(DEFAULT_SERVER_URL)
                val me = base.me()
                val token = base.realtimeToken().access_token
                val org = me.organization_id ?: throw IllegalStateException("No organization")
                val url = BuildConfig.SUPABASE_URL
                    .replaceFirst(Regex("^http"), "ws")
                    .trimEnd('/') + "/realtime/v1/websocket?apikey=" +
                    java.net.URLEncoder.encode(BuildConfig.SUPABASE_PUBLISHABLE_KEY, "UTF-8") +
                    "&vsn=1.0.0"

                val request = Request.Builder().url(url).build()
                socket = client.newWebSocket(request, listener(org, token))
            } catch (t: Throwable) {
                onStatus("reconnecting")
                scheduleReconnect()
            }
        }
    }

    private fun listener(org: Int, token: String) = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            attempt = 0
            onStatus("connected")
            val topic = "realtime:org:$org:telemetry"
            ws.send(JSONObject().apply {
                put("ref", "1")
                put("join_ref", "1")
                put("topic", topic)
                put("event", "phx_join")
                put("payload", JSONObject().apply {
                    put("config", JSONObject().apply { put("broadcast", JSONObject().put("self", false)); put("private", true) })
                    put("access_token", token)
                })
            }.toString())
        }

        override fun onMessage(ws: WebSocket, text: String) {
            try {
                val msg = JSONObject(text)
                if (msg.optString("event") != "broadcast") return
                val payload = msg.optJSONObject("payload")?.let { outer ->
                    outer.optJSONObject("payload") ?: outer
                } ?: return
                if (payload.optString("type") != "telemetry") return
                onReading(LiveTelemetry(
                    machineId = payload.optInt("machine_id"),
                    machineName = payload.optString("machine", null),
                    readingType = payload.optString("reading_type"),
                    value = payload.optDouble("value"),
                    unit = payload.optString("unit", null),
                    recordedAt = payload.optString("recorded_at", null)
                ))
            } catch (t: Throwable) {
                Log.w("MaintainRealtime", "Invalid telemetry event", t)
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            onStatus("reconnecting")
            socket = null
            scheduleReconnect()
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            socket = null
            if (!stopped) {
                onStatus("reconnecting")
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (stopped || reconnectJob?.isActive == true) return
        val delayMs = minOf(30_000L, 1_000L * (1L shl minOf(attempt++, 5)))
        reconnectJob = scope.launch {
            delay(delayMs)
            reconnectJob = null
            connect()
        }
    }
}
