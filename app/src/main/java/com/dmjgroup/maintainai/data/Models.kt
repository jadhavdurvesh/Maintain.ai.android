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
    @SerializedName("alert_type") val alertType: String? = null,
    val message: String? = null,
    val severity: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val acknowledged: Boolean = false,
    val resolved: Boolean = false
)

data class WorkOrder(
    val id: Int = 0,
    @SerializedName("machine_id") val machineId: Int = 0,
    val problem: String? = null,
    val priority: String? = null,
    val status: String? = null,
    @SerializedName("recommended_actions") val recommendedActions: String? = null,
    @SerializedName("assigned_to") val assignedTo: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null,
    @SerializedName("resolution_notes") val resolutionNotes: String? = null
)

data class AiModelInsight(
    val id: String? = null,
    @SerializedName("machine_id") val machineId: Int? = null,
    @SerializedName("machine_name") val machineName: String? = null,
    @SerializedName("model_name") val modelName: String? = null,
    @SerializedName("model_version") val modelVersion: String? = null,
    val status: String? = null,
    @SerializedName("risk_score") val riskScore: Double? = null,
    @SerializedName("anomaly_score") val anomalyScore: Double? = null,
    val confidence: Double? = null,
    @SerializedName("predicted_failure_window") val predictedFailureWindow: String? = null,
    val diagnosis: String? = null,
    @SerializedName("recommended_action") val recommendedAction: String? = null,
    @SerializedName("generated_at") val generatedAt: String? = null
)

data class DashboardData(
    val machines: List<Machine> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val workOrders: List<WorkOrder> = emptyList(),
    val aiInsights: List<AiModelInsight> = emptyList()
)
