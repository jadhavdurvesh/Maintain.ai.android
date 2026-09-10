package com.dmjgroup.maintainai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.dmjgroup.maintainai.data.DashboardData
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PDF_WIDTH = 595
private const val PDF_HEIGHT = 842
private const val LEFT = 40f
private const val RIGHT = 555f

fun createMaintenanceReportPdf(context: Context, data: DashboardData): File {
    val document = PdfDocument()
    var pageNumber = 1
    var page = document.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pageNumber).create())
    var canvas = page.canvas
    var y = drawPageHeader(canvas)

    val machines = data.machines
    val alerts = data.alerts
    val workOrders = data.workOrders
    val insights = data.aiInsights
    val healthValues = machines.mapNotNull { it.healthScore }
    val avgHealth = if (healthValues.isEmpty()) 0.0 else healthValues.average()
    val openAlerts = alerts.count { !it.resolved }
    val openOrders = workOrders.count { it.status?.lowercase() != "completed" }
    val healthy = machines.count { (it.healthScore ?: 0.0) >= 70 }
    val attention = machines.count { val h = it.healthScore ?: 0.0; h in 40.0..69.999 }
    val critical = machines.count { (it.healthScore ?: 0.0) < 40 }

    fun ensureSpace(required: Float = 32f) {
        if (y + required <= 790f) return
        drawFooter(canvas, pageNumber)
        document.finishPage(page)
        pageNumber += 1
        page = document.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pageNumber).create())
        canvas = page.canvas
        y = drawPageHeader(canvas)
    }

    y = drawSectionTitle(canvas, "Maintenance Snapshot", y)
    y = drawKeyValue(canvas, "Overall fleet health", "%.1f%%".format(Locale.US, avgHealth), y)
    y = drawKeyValue(canvas, "Machines", machines.size.toString(), y)
    y = drawKeyValue(canvas, "Healthy / Attention / Critical", "$healthy / $attention / $critical", y)
    y = drawKeyValue(canvas, "Open alerts", openAlerts.toString(), y)
    y = drawKeyValue(canvas, "Open work orders", openOrders.toString(), y)
    y = drawKeyValue(canvas, "AI predictions", insights.size.toString(), y)
    y += 12f

    ensureSpace(90f)
    y = drawSectionTitle(canvas, "Machine Health", y)
    if (machines.isEmpty()) {
        y = drawBody(canvas, "No machine data is available in the current mobile session.", y)
    } else {
        machines.forEach { machine ->
            ensureSpace(62f)
            val health = machine.healthScore?.let { "%.1f%%".format(Locale.US, it) } ?: "No data"
            y = drawRow(canvas, machine.name, "${machine.machineCode ?: "Machine #${machine.id}"}  •  Health $health", y)
        }
    }
    y += 8f

    ensureSpace(90f)
    y = drawSectionTitle(canvas, "Alerts", y)
    val listedAlerts = alerts.take(12)
    if (listedAlerts.isEmpty()) {
        y = drawBody(canvas, "No alerts were returned by the backend.", y)
    } else {
        listedAlerts.forEach { alert ->
            ensureSpace(72f)
            val severity = alert.severity?.uppercase() ?: "UNKNOWN"
            val state = if (alert.resolved) "RESOLVED" else if (alert.acknowledged) "ACKNOWLEDGED" else "OPEN"
            y = drawRow(canvas, "${severity} • $state", alert.message ?: alert.alertType ?: "Maintenance alert", y)
        }
        if (alerts.size > listedAlerts.size) y = drawBody(canvas, "${alerts.size - listedAlerts.size} additional alerts omitted for report size.", y)
    }
    y += 8f

    ensureSpace(90f)
    y = drawSectionTitle(canvas, "Work Orders", y)
    val listedOrders = workOrders.take(12)
    if (listedOrders.isEmpty()) {
        y = drawBody(canvas, "No work orders are currently available.", y)
    } else {
        listedOrders.forEach { order ->
            ensureSpace(76f)
            val status = order.status?.uppercase() ?: "UNKNOWN"
            val priority = order.priority?.uppercase() ?: "MEDIUM"
            y = drawRow(canvas, "WO-${order.id} • $priority • $status", order.problem ?: "Maintenance work order", y)
            val assignee = order.assignedTo?.takeIf { it.isNotBlank() }?.let { "Assigned: $it" }
            if (assignee != null) y = drawBody(canvas, assignee, y, 13f)
        }
        if (workOrders.size > listedOrders.size) y = drawBody(canvas, "${workOrders.size - listedOrders.size} additional work orders omitted for report size.", y)
    }
    y += 8f

    ensureSpace(90f)
    y = drawSectionTitle(canvas, "AI Model Output", y)
    val model = data.modelStatus
    y = drawKeyValue(canvas, "Model", "MAINTAIN AI Random Forest", y)
    y = drawKeyValue(canvas, "Published", if (model?.trained == true) "Yes" else "No", y)
    model?.modelVersion?.let { y = drawKeyValue(canvas, "Version", it.toString(), y) }
    model?.nSamples?.let { y = drawKeyValue(canvas, "Training samples", it.toString(), y) }
    if (!model?.reason.isNullOrBlank()) y = drawBody(canvas, model?.reason.orEmpty(), y)
    insights.take(10).forEach { insight ->
        ensureSpace(74f)
        val predicted = insight.predictedHealthScore?.let { "%.1f%%".format(Locale.US, it) } ?: "—"
        val risk = insight.riskLevel?.uppercase() ?: "UNKNOWN"
        y = drawRow(canvas, "${insight.machineName ?: "Machine #${insight.machineId}"} • $risk", "Predicted health $predicted • ${insight.reason ?: "Prediction available."}", y)
    }
    if (insights.size > 10) y = drawBody(canvas, "${insights.size - 10} additional AI predictions omitted for report size.", y)

    drawFooter(canvas, pageNumber)
    document.finishPage(page)

    val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val file = File(reportsDir, "MAINTAIN_AI_Maintenance_Report_$timestamp.pdf")
    FileOutputStream(file).use { document.writeTo(it) }
    document.close()
    return file
}

fun maintenanceReportUri(context: Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

private fun textPaint(size: Float, bold: Boolean = false): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    color = android.graphics.Color.rgb(35, 48, 64)
    typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
}

private fun drawPageHeader(canvas: Canvas): Float {
    val title = textPaint(24f, true)
    canvas.drawText("MAINTAIN AI", LEFT, 44f, title)
    val sub = textPaint(11f)
    sub.color = android.graphics.Color.rgb(95, 108, 124)
    canvas.drawText("Predictive Maintenance Report", LEFT, 63f, sub)
    val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date())
    canvas.drawText(date, RIGHT - sub.measureText(date), 44f, sub)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(195, 206, 218); strokeWidth = 1.5f }
    canvas.drawLine(LEFT, 78f, RIGHT, 78f, line)
    return 106f
}

private fun drawSectionTitle(canvas: Canvas, text: String, y: Float): Float {
    val paint = textPaint(16f, true)
    canvas.drawText(text, LEFT, y, paint)
    return y + 26f
}

private fun drawKeyValue(canvas: Canvas, label: String, value: String, y: Float): Float {
    val labelPaint = textPaint(11f, true)
    val valuePaint = textPaint(12f)
    canvas.drawText(label, LEFT, y, labelPaint)
    canvas.drawText(value, LEFT + 185f, y, valuePaint)
    return y + 20f
}

private fun drawRow(canvas: Canvas, title: String, body: String, y: Float): Float {
    val titlePaint = textPaint(11.5f, true)
    val bodyPaint = textPaint(10.5f)
    canvas.drawText(title.take(88), LEFT, y, titlePaint)
    var bodyY = y + 16f
    bodyY = drawWrapped(canvas, body, LEFT, bodyY, RIGHT - LEFT, bodyPaint, 13f, 2)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(224, 230, 237); strokeWidth = 1f }
    canvas.drawLine(LEFT, bodyY + 5f, RIGHT, bodyY + 5f, line)
    return bodyY + 20f
}

private fun drawBody(canvas: Canvas, text: String, y: Float, gap: Float = 16f): Float {
    val paint = textPaint(10.5f)
    return drawWrapped(canvas, text, LEFT, y, RIGHT - LEFT, paint, 13f, 3) + gap
}

private fun drawWrapped(canvas: Canvas, text: String, x: Float, startY: Float, maxWidth: Float, paint: Paint, lineHeight: Float, maxLines: Int): Float {
    val words = text.replace("\n", " ").split(Regex("\\s+")).filter { it.isNotBlank() }
    var line = ""
    var y = startY
    var lines = 0
    for (word in words) {
        val candidate = if (line.isEmpty()) word else "$line $word"
        if (paint.measureText(candidate) <= maxWidth) {
            line = candidate
        } else {
            if (line.isNotEmpty()) {
                canvas.drawText(line, x, y, paint)
                y += lineHeight
                lines++
                if (lines >= maxLines) return y
            }
            line = word
        }
    }
    if (line.isNotEmpty() && lines < maxLines) {
        canvas.drawText(line, x, y, paint)
        y += lineHeight
    }
    return y
}

private fun drawFooter(canvas: Canvas, pageNumber: Int) {
    val paint = textPaint(9f)
    paint.color = android.graphics.Color.rgb(120, 132, 147)
    val text = "MAINTAIN AI  •  Confidential maintenance report  •  Page $pageNumber"
    canvas.drawText(text, LEFT, 818f, paint)
}
