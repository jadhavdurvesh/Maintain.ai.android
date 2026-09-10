package com.dmjgroup.maintainai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dmjgroup.maintainai.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private val AppBackground = Color(0xFF08111F)
private val Surface = Color(0xFF101C2D)
private val Surface2 = Color(0xFF16243A)
private val Cyan = Color(0xFF55D6FF)
private val Green = Color(0xFF45D483)
private val Amber = Color(0xFFFFC857)
private val Red = Color(0xFFFF5C70)
private val TextPrimary = Color(0xFFF2F6FC)
private val TextMuted = Color(0xFF8FA1B8)

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        setContent { MaintainApp() }
        runCatching { scheduleAlertWorker() }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            runCatching { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("alerts", "MAINTAIN AI Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Critical maintenance and machine alerts"
                }
            )
        }
    }

    private fun scheduleAlertWorker() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "maintain-ai-alert-check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES).build()
        )
    }
}

class MainViewModel : ViewModel() {
    var data by mutableStateOf(DashboardData()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var serverUrl by mutableStateOf(DEFAULT_SERVER_URL); private set
    var lastUpdated by mutableStateOf(""); private set
    private var syncing = false

    init {
        viewModelScope.launch {
            refreshSafely(silent = true)
            while (isActive) {
                delay(10_000)
                refreshSafely(silent = true)
            }
        }
    }

    fun updateServerUrl(url: String) {
        serverUrl = url.trim().ifEmpty { DEFAULT_SERVER_URL }
    }

    fun refresh() = viewModelScope.launch { refreshSafely(silent = false) }

    private suspend fun refreshSafely(silent: Boolean) {
        if (syncing) return
        syncing = true
        try {
            if (!silent) {
                loading = true
                error = null
            }
            val result = runCatching { MaintainRepository().load(serverUrl) }
            result.onSuccess {
                data = it
                lastUpdated = "Live"
            }.onFailure {
                if (!silent || (data.machines.isEmpty() && data.alerts.isEmpty() && data.workOrders.isEmpty())) {
                    error = it.message ?: "Unable to connect"
                }
            }
        } catch (t: Throwable) {
            if (!silent || (data.machines.isEmpty() && data.alerts.isEmpty() && data.workOrders.isEmpty())) {
                error = t.message ?: "Unable to load MAINTAIN AI"
            }
        } finally {
            syncing = false
            if (!silent) loading = false
        }
    }
}

@Composable
fun MaintainApp(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            background = AppBackground,
            surface = Surface,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        )
    ) {
        Scaffold(
            containerColor = AppBackground,
            bottomBar = { BottomNav(nav) }
        ) { padding ->
            NavHost(nav, startDestination = "dashboard", modifier = Modifier.padding(padding)) {
                composable("dashboard") { DashboardScreen(vm) }
                composable("alerts") { AlertsScreen(vm.data.alerts) }
                composable("analytics") { AnalyticsScreen(vm.data.machines, vm.data.aiInsights, vm.data.modelStatus) }
                composable("workorders") { WorkOrdersScreen(vm.data.workOrders, vm.data.machines) }
                composable("more") { MoreScreen(vm.data, vm.serverUrl, nav) { vm.updateServerUrl(it); vm.refresh() } }
                composable("reports") { ReportsScreen(vm.data, nav) }
                composable("settings") { SettingsScreen(vm.serverUrl) { vm.updateServerUrl(it); vm.refresh() } }
            }
        }
    }
}

@Composable
private fun BottomNav(nav: NavHostController) {
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    val items = listOf(
        "dashboard" to ("Overview" to Icons.Default.Dashboard),
        "alerts" to ("Alerts" to Icons.Default.Notifications),
        "analytics" to ("Analytics" to Icons.Default.Insights),
        "workorders" to ("Work Orders" to Icons.Default.Build),
        "more" to ("More" to Icons.Default.MoreHoriz)
    )
    Surface(color = Color(0xFF0C1727), shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(74.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (route, item) ->
                val selected = current == route
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable {
                        nav.navigate(route) {
                            launchSingleTop = true
                            popUpTo("dashboard") { saveState = true }
                        }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Cyan.copy(alpha = .12f) else Color.Transparent)
                            .padding(horizontal = 11.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            item.second,
                            contentDescription = item.first,
                            tint = if (selected) Cyan else TextMuted,
                            modifier = Modifier.size(if (selected) 30.dp else 22.dp)
                        )
                    }
                    if (selected) {
                        Text(item.first, color = Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(vm: MainViewModel) {
    val machines = vm.data.machines
    val healthy = machines.count { (it.healthScore ?: 0.0) >= 70 }
    val attention = machines.count { val h = it.healthScore ?: 0.0; h in 40.0..69.999 }
    val critical = machines.count { (it.healthScore ?: 0.0) < 40 }
    val avg = machines.mapNotNull { it.healthScore }.let { if (it.isEmpty()) 0.0 else it.average() }
    val urgent = vm.data.alerts.count { it.severity?.lowercase() in setOf("urgent", "critical", "high") && !it.resolved }
    val openOrders = vm.data.workOrders.count { it.status?.lowercase() != "completed" }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MAINTAIN AI", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Predictive maintenance", color = TextMuted)
                }
                StatusPill("LIVE", Green)
            }
        }
        vm.error?.let { message -> item { ErrorCard(message) } }
        item { FleetHealthCard(avg, vm.loading, vm.lastUpdated) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Machines", machines.size.toString(), Icons.Default.Factory, Cyan, Modifier.weight(1f))
                MetricCard("Critical", critical.toString(), Icons.Default.Warning, Red, Modifier.weight(1f))
                MetricCard("Urgent", urgent.toString(), Icons.Default.NotificationsActive, Amber, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Open orders", openOrders.toString(), Icons.Default.Build, Amber, Modifier.weight(1f))
                MetricCard("Healthy", healthy.toString(), Icons.Default.CheckCircle, Green, Modifier.weight(1f))
            }
        }
        item { CompactHealthDistribution(healthy, attention, critical, machines.size) }
        item { SectionTitle("Predictive model", "Local model publication"); Spacer(Modifier.height(6.dp)); ModelPublicationCard(vm.data.modelStatus, vm.data.aiInsights) }
        item { SectionTitle("Recent work", "Latest maintenance activity") }
        if (vm.data.workOrders.isEmpty()) item { EmptyState("No work orders", "New maintenance jobs will appear automatically.") }
        else items(vm.data.workOrders.take(3)) { WorkOrderPreview(it, machines) }
        item { SectionTitle("Fleet", "Live machine health") }
        if (machines.isEmpty()) item { EmptyState("No machine data", "Connect to the MAINTAIN AI backend to see your fleet.") }
        items(machines) { MachineCard(it) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun FleetHealthCard(avg: Double, loading: Boolean, updated: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fleet health", color = TextMuted)
                    Text(if (avg == 0.0) "—" else "%.0f%%".format(avg), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                }
                Text(if (loading) "Syncing" else updated.ifEmpty { "Live" }, color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (avg / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                color = if (avg >= 70) Green else if (avg >= 40) Amber else Red,
                trackColor = Surface2
            )
            Spacer(Modifier.height(7.dp))
            Text("Auto-synced every 10 seconds", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CompactHealthDistribution(healthy: Int, attention: Int, critical: Int, total: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            HealthPie(healthy, attention, critical, Modifier.size(92.dp))
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Health distribution", fontWeight = FontWeight.Bold)
                LegendRow("Healthy", healthy, Green, total)
                LegendRow("Attention", attention, Amber, total)
                LegendRow("Critical", critical, Red, total)
            }
        }
    }
}

@Composable
private fun ModelPublicationCard(status: ModelStatus?, insights: List<AiModelInsight>) {
    val trained = status?.trained == true
    val accent = if (trained) Green else Amber
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Surface2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Psychology, null, tint = Cyan)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("MAINTAIN AI Random Forest", fontWeight = FontWeight.Bold)
                    Text(if (trained) "Published to mobile" else "Not published", color = accent, style = MaterialTheme.typography.labelSmall)
                }
                MiniChip(if (trained) "READY" else "NOT READY", accent)
            }
            Spacer(Modifier.height(10.dp))
            if (trained) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${status?.nSamples ?: 0} samples", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    status?.modelVersion?.let { Text("v$it", color = TextMuted, style = MaterialTheme.typography.bodySmall) }
                    Text("${insights.size} predictions", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                status?.trainedAt?.let { Text("Trained: ${formatTimestamp(it)}", color = TextMuted, style = MaterialTheme.typography.bodySmall) }
            } else {
                Text(status?.reason ?: "The backend has not reported a trained model.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(11.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title, color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun WorkOrderPreview(order: WorkOrder, machines: List<Machine>) {
    val machine = machines.firstOrNull { it.id == order.machineId }
    val priority = order.priority?.uppercase() ?: "MEDIUM"
    val color = if (priority == "CRITICAL" || priority == "HIGH") Red else Amber
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Build, null, tint = Cyan)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("WO-${order.id} • ${machine?.name ?: "Machine #${order.machineId}"}", fontWeight = FontWeight.Bold, softWrap = true)
                Text(order.problem ?: "Maintenance work order", color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            MiniChip(priority, color)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HealthPie(healthy: Int, attention: Int, critical: Int, modifier: Modifier) {
    val total = (healthy + attention + critical).coerceAtLeast(1)
    Canvas(modifier) {
        var start = -90f
        listOf(healthy to Green, attention to Amber, critical to Red).forEach { (count, color) ->
            val sweep = 360f * count / total
            drawArc(color, start, sweep, false, style = Stroke(width = 22f, cap = StrokeCap.Butt))
            start += sweep
        }
        drawCircle(Surface, radius = size.minDimension * .26f)
    }
}

@Composable
private fun LegendRow(label: String, count: Int, color: Color, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(label, color = TextMuted, modifier = Modifier.width(68.dp), style = MaterialTheme.typography.bodySmall)
        Text(count.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        if (total > 0) Text("  ${count * 100 / total}%", color = TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MachineCard(m: Machine) {
    val health = m.healthScore
    val status = when {
        health == null -> "NO DATA"
        health >= 70 -> "HEALTHY"
        health >= 40 -> "ATTENTION"
        else -> "CRITICAL"
    }
    val accent = when (status) {
        "HEALTHY" -> Green
        "ATTENTION" -> Amber
        "CRITICAL" -> Red
        else -> TextMuted
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Surface2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PrecisionManufacturing, null, tint = Cyan)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, fontWeight = FontWeight.SemiBold, softWrap = true)
                    Text(m.machineCode ?: "No machine code", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                MiniChip(status, accent)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Health", color = TextMuted, modifier = Modifier.width(52.dp), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { ((health ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                    color = accent,
                    trackColor = Surface2
                )
                Spacer(Modifier.width(9.dp))
                Text(health?.let { "%.0f%%".format(it) } ?: "—", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AiInsightCard(i: AiModelInsight) {
    val predicted = i.predictedHealthScore ?: 0.0
    val risk = (100.0 - predicted).coerceIn(0.0, 100.0)
    val accent = when (i.riskLevel?.lowercase()) { "high" -> Red; "medium" -> Amber; else -> Green }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = Cyan)
                Spacer(Modifier.width(8.dp))
                Text(i.machineName ?: "Machine", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), softWrap = true)
                i.modelVersion?.let { Text("v$it", color = TextMuted, style = MaterialTheme.typography.labelSmall) }
            }
            Spacer(Modifier.height(8.dp))
            Text(i.reason ?: "Prediction available.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MiniChip("Risk ${"%.0f".format(risk)}%", accent)
                MiniChip("Predicted health ${"%.0f".format(predicted)}%", Cyan)
            }
        }
    }
}

@Composable
private fun AlertsScreen(alerts: List<Alert>) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Alerts", "Issues requiring attention") }
        if (alerts.isEmpty()) item { EmptyState("All clear", "No alerts were returned by the backend.") }
        items(alerts) { AlertCard(it) }
    }
}

@Composable
private fun AlertCard(a: Alert) {
    val severity = a.severity?.uppercase() ?: "UNKNOWN"
    val accent = when (severity) { "URGENT", "CRITICAL", "HIGH" -> Red; "MEDIUM" -> Amber; else -> Cyan }
    val state = if (a.resolved) "RESOLVED" else if (a.acknowledged) "ACKNOWLEDGED" else "OPEN"
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(17.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
            Icon(if (severity in setOf("URGENT", "CRITICAL", "HIGH")) Icons.Default.Warning else Icons.Default.Info, null, tint = accent)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.alertType ?: "Maintenance alert", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    MiniChip(severity, accent)
                }
                Spacer(Modifier.height(5.dp))
                Text(a.message ?: "No additional information.", color = TextMuted)
                Spacer(Modifier.height(6.dp))
                Text(state, color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AnalyticsScreen(machines: List<Machine>, insights: List<AiModelInsight>, modelStatus: ModelStatus?) {
    val healthy = machines.count { (it.healthScore ?: 0.0) >= 70 }
    val attention = machines.count { val h = it.healthScore ?: 0.0; h in 40.0..69.999 }
    val critical = machines.count { (it.healthScore ?: 0.0) < 40 }
    val avg = machines.mapNotNull { it.healthScore }.let { if (it.isEmpty()) 0.0 else it.average() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Analytics", "Fleet condition and model output") }
        item { ReportRow("Average health", "%.1f%%".format(avg), Icons.Default.Favorite) }
        item { CompactHealthDistribution(healthy, attention, critical, machines.size) }
        item { SectionTitle("Local model", "Publication status") }
        item { ModelPublicationCard(modelStatus, insights) }
        item { SectionTitle("Predictions", "Current model output") }
        if (insights.isEmpty()) item { EmptyState("No predictions", modelStatus?.reason ?: "The trained model has not returned predictions.") }
        else items(insights) { AiInsightCard(it) }
    }
}

@Composable
private fun MoreScreen(data: DashboardData, url: String, nav: NavHostController, onSave: (String) -> Unit) {
    val avg = data.machines.mapNotNull { it.healthScore }.let { if (it.isEmpty()) 0.0 else it.average() }
    val openAlerts = data.alerts.count { !it.resolved }
    val openOrders = data.workOrders.count { it.status?.lowercase() != "completed" }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("More", "Reports, exports and connection") }
        item {
            MoreActionCard("Reports", "View, generate and export a maintenance report", Icons.Default.Assessment) {
                nav.navigate("reports") { launchSingleTop = true }
            }
        }
        item { ReportHero(data.machines.size, avg, openAlerts, openOrders) }
        item { ReportRow("AI predictions", data.aiInsights.size.toString(), Icons.Default.AutoAwesome) }
        item { Text("Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { SettingsCard(url, onSave) }
    }
}

@Composable
private fun ReportsScreen(data: DashboardData, nav: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var reportFile by remember { mutableStateOf<File?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val source = reportFile
        if (uri != null && source != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Unable to open destination")
                statusMessage = "PDF saved successfully"
            }.onFailure { statusMessage = "Could not save PDF: ${it.message ?: "unknown error"}" }
        }
    }

    fun ensureReport(): File? {
        return reportFile ?: runCatching { createMaintenanceReportPdf(context, data) }
            .onFailure { statusMessage = "Could not generate PDF: ${it.message ?: "unknown error"}" }
            .getOrNull()
            ?.also { reportFile = it }
    }

    fun openReport() {
        val file = ensureReport() ?: return
        runCatching {
            val uri = maintenanceReportUri(context, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open MAINTAIN AI report"))
        }.onFailure { statusMessage = "No PDF viewer is available on this device" }
    }

    fun shareReport() {
        val file = ensureReport() ?: return
        runCatching {
            val uri = maintenanceReportUri(context, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share MAINTAIN AI report"))
        }.onFailure { statusMessage = "Could not share the report" }
    }

    val machines = data.machines
    val avg = machines.mapNotNull { it.healthScore }.let { if (it.isEmpty()) 0.0 else it.average() }
    val openAlerts = data.alerts.count { !it.resolved }
    val openOrders = data.workOrders.count { it.status?.lowercase() != "completed" }
    val healthy = machines.count { (it.healthScore ?: 0.0) >= 70 }
    val attention = machines.count { val h = it.healthScore ?: 0.0; h in 40.0..69.999 }
    val critical = machines.count { (it.healthScore ?: 0.0) < 40 }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Reports", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Management-ready maintenance overview", color = TextMuted)
                }
            }
        }
        item { ReportHero(machines.size, avg, openAlerts, openOrders) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Export report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("Generate a PDF containing the current fleet health, machines, alerts, work orders and AI model output.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { reportFile = null; statusMessage = null; ensureReport() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PictureAsPdf, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (reportFile == null) "Generate PDF" else "Regenerate PDF")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openReport() }, modifier = Modifier.weight(1f), enabled = reportFile != null) {
                            Icon(Icons.Default.Visibility, null); Spacer(Modifier.width(5.dp)); Text("Open")
                        }
                        OutlinedButton(onClick = { saveLauncher.launch(reportFile?.name ?: "MAINTAIN_AI_Maintenance_Report.pdf") }, modifier = Modifier.weight(1f), enabled = reportFile != null) {
                            Icon(Icons.Default.Download, null); Spacer(Modifier.width(5.dp)); Text("Save")
                        }
                        OutlinedButton(onClick = { shareReport() }, modifier = Modifier.weight(1f), enabled = reportFile != null) {
                            Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Share")
                        }
                    }
                    statusMessage?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = if (it.startsWith("Could") || it.startsWith("No PDF")) Red else Green, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { SectionTitle("Fleet health", "Current machine condition") }
        item { CompactHealthDistribution(healthy, attention, critical, machines.size) }
        item { SectionTitle("Machine status", "Included in the exported report") }
        if (machines.isEmpty()) item { EmptyState("No machines", "No machine data is available for the report.") }
        else items(machines) { ReportMachineRow(it) }
        item { SectionTitle("Alerts", "$openAlerts open") }
        if (data.alerts.isEmpty()) item { EmptyState("No alerts", "No alerts are currently available.") }
        else items(data.alerts.take(8)) { ReportListItem(it.severity?.uppercase() ?: "UNKNOWN", it.message ?: it.alertType ?: "Maintenance alert") }
        item { SectionTitle("Work orders", "$openOrders open") }
        if (data.workOrders.isEmpty()) item { EmptyState("No work orders", "No maintenance jobs are currently available.") }
        else items(data.workOrders.take(8)) { order -> ReportListItem("WO-${order.id} • ${order.status?.uppercase() ?: "UNKNOWN"}", order.problem ?: "Maintenance work order") }
        item { SectionTitle("AI predictions", "Current model output") }
        if (data.aiInsights.isEmpty()) item { EmptyState("No predictions", data.modelStatus?.reason ?: "No AI predictions are available.") }
        else items(data.aiInsights.take(8)) { insight -> ReportListItem(insight.machineName ?: "Machine #${insight.machineId}", "Predicted health ${insight.predictedHealthScore?.let { "%.1f%%".format(it) } ?: "—"} • ${insight.riskLevel ?: "UNKNOWN"}") }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ReportMachineRow(machine: Machine) {
    val health = machine.healthScore
    val status = when { health == null -> "NO DATA"; health >= 70 -> "HEALTHY"; health >= 40 -> "ATTENTION"; else -> "CRITICAL" }
    val accent = when (status) { "HEALTHY" -> Green; "ATTENTION" -> Amber; "CRITICAL" -> Red; else -> TextMuted }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(machine.name, fontWeight = FontWeight.SemiBold)
                Text(machine.machineCode ?: "No machine code", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(status, color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(health?.let { "%.1f%%".format(it) } ?: "—", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReportListItem(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsCard(url: String, onSave: (String) -> Unit) {
    var text by remember(url) { mutableStateOf(url) }
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(15.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = { onSave(text) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CloudDone, null)
                Spacer(Modifier.width(8.dp))
                Text("Save & connect")
            }
            Spacer(Modifier.height(8.dp))
            Text("Default: https://maintain-ai-3.vercel.app/\nLocal phone: use your computer's LAN IP on port 8000.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(5.dp))
            Text("Automatic sync is active while the app is open.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsScreen(url: String, onSave: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Settings", "Connection and monitoring") }
        item { SettingsCard(url, onSave) }
    }
}

@Composable
private fun ReportHero(machines: Int, avg: Double, alerts: Int, workOrders: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(19.dp)) {
        Column(Modifier.padding(17.dp)) {
            Text("Maintenance snapshot", color = TextMuted)
            Text(if (machines == 0) "—" else "%.0f%%".format(avg), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text("overall fleet health", color = TextMuted)
            Spacer(Modifier.height(10.dp))
            Text("$machines machines  •  $alerts alerts  •  $workOrders orders", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Cyan)
            Spacer(Modifier.width(11.dp))
            Text(label, Modifier.weight(1f))
            Text(value, color = TextMuted)
        }
    }
}

@Composable
private fun MoreActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(17.dp)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Surface2), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Cyan)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextMuted)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MiniChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(50)) {
        Text(text, color = color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

private fun formatTimestamp(value: String): String = value.replace('T', ' ').substringBefore('.')

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CloudOff, null, tint = TextMuted, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF321820)), shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudOff, null, tint = Red)
            Spacer(Modifier.width(9.dp))
            Text("Connection problem: $message", color = Color(0xFFFFB8C0), style = MaterialTheme.typography.bodySmall)
        }
    }
}
