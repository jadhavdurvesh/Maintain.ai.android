package com.dmjgroup.maintainai.data

import com.google.gson.annotations.SerializedName

data class Machine(
    val id: Int = 0,
    @SerializedName("machine_code") val machineCode: String? = null,
    val name: String = "Unknown machine",
    val category: String? = null,
    val manufacturer: String? = null,
    val location: String? = null,
    val department: String? = null,
    @SerializedName("health_score") val healthScore: Double? = null,
    val status: String? = null,
    @SerializedName("operating_hours") val operatingHours: Double? = null,
    @SerializedName("next_maintenance") val nextMaintenance: String? = null
)

data class SensorReading(
    val id: Int = 0,
    @SerializedName("machine_id") val machineId: Int = 0,
    @SerializedName("reading_type") val readingType: String = "",
    val value: Double = 0.0,
    val unit: String? = null,
    val source: String? = null,
    @SerializedName("recorded_at") val recordedAt: String? = null
)

data class Alert(
    val id: Int = 0,
    @SerializedName("machine_id") val machineId: Int? = null,
    val title: String? = null,
    val message: String? = null,
    val severity: String? = null,
    val status: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class WorkOrder(
    val id: Int = 0,
    @SerializedName("machine_id") val machineId: Int? = null,
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    val priority: String? = null,
    @SerializedName("due_date") val dueDate: String? = null
)

data class DashboardData(
    val machines: List<Machine> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val workOrders: List<WorkOrder> = emptyList()
)
