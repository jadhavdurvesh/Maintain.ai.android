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

/** Mirrors GET /api/analytics/model-status. */
data class ModelStatus(
    val trained: Boolean = false,
    @SerializedName("trained_at") val trainedAt: String? = null,
    @SerializedName("n_samples") val nSamples: Int? = null,
    @SerializedName("model_version") val modelVersion: Int? = null,
    @SerializedName("sensor_aware") val sensorAware: Boolean? = null,
    val reason: String? = null
)

data class RiskPrediction(
    @SerializedName("machine_id") val machineId: Int = 0,
    @SerializedName("machine_name") val machineName: String? = null,
    @SerializedName("actual_health_score") val actualHealthScore: Double? = null,
    @SerializedName("predicted_health_score") val predictedHealthScore: Double? = null,
    @SerializedName("risk_level") val riskLevel: String? = null,
    val reason: String? = null
)

data class RiskPredictionsResponse(
    val available: Boolean = false,
    @SerializedName("trained_at") val trainedAt: String? = null,
    @SerializedName("n_samples") val nSamples: Int? = null,
    @SerializedName("model_version") val modelVersion: Int? = null,
    @SerializedName("sensor_aware") val sensorAware: Boolean? = null,
    val reason: String? = null,
    val predictions: List<RiskPrediction> = emptyList()
)

data class TrainModelResponse(
    val trained: Boolean = false,
    @SerializedName("n_samples") val nSamples: Int? = null,
    @SerializedName("model_version") val modelVersion: Int? = null,
    @SerializedName("sensor_aware") val sensorAware: Boolean? = null,
    val reason: String? = null
)

data class AiModelInsight(
    val machineId: Int = 0,
    val machineName: String? = null,
    val actualHealthScore: Double? = null,
    val predictedHealthScore: Double? = null,
    val riskLevel: String? = null,
    val reason: String? = null,
    val modelVersion: Int? = null,
    val trainedAt: String? = null,
    val diagnosis: String? = reason,
    val recommendedAction: String? = null,
    val riskScore: Double? = predictedHealthScore?.let { (100.0 - it).coerceIn(0.0, 100.0) },
    val modelName: String? = "MAINTAIN AI Random Forest"
)

data class DashboardData(
    val machines: List<Machine> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val workOrders: List<WorkOrder> = emptyList(),
    val aiInsights: List<AiModelInsight> = emptyList(),
    val modelStatus: ModelStatus? = null
)