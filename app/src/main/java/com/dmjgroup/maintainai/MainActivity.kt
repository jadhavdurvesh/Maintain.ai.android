package com.dmjgroup.maintainai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        scheduleAlertWorker()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { MaintainApp() }
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

    init {
        viewModelScope.launch {
            sync(silent = true)
            while (isActive) {
                delay(30_000)
                sync(silent = true)
            }
        }
    }

    fun updateServerUrl(url: String) {
        serverUrl = url.trim().ifEmpty { DEFAULT_SERVER_URL }
    }

    fun refresh() = viewModelScope.launch { sync(silent = false) }

    private suspend fun sync(silent: Boolean) {
        if (!silent) {
            loading = true
            error = null
        }
        runCatching { MaintainRepository().load(serverUrl) }
            .onSuccess {
                data = it
                lastUpdated = "Live"
            }
            .onFailure {
                if (!silent) error = it.message ?: "Unable to connect"
            }
        if (!silent) loading = false
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
                composable("analytics") { AnalyticsScreen(vm.data.machines) }
                composable("reports") { ReportsScreen(vm.data) }
                composable("workorders") { WorkOrdersScreen(vm.data.workOrders, vm.data.machines) }
                composable("settings") { SettingsScreen(vm.serverUrl) { vm.updateServerUrl(it); vm.refresh() } }
            }
        }
    }
}

@Composable
private fun BottomNav(nav: NavHostController) {
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    val items = listOf(
        "dashboard" to Icons.Default.Dashboard,
        "alerts" to Icons.Default.Notifications,
        "analytics" to Icons.Default.Insights,
        "reports" to Icons.Default.Assessment,
        "workorders" to Icons.Default.Build,
        "settings" to Icons.Default.Settings
    )
    NavigationBar(containerColor = Color(0xFF0C1727)) {
        items.forEach { (route, icon) ->
            NavigationBarItem(
                selected = current == route,
                onClick = { nav.navigate(route) { launchSingleTop = true } },
                icon = { Icon(icon, null) },
                label = { Text(route.replaceFirstChar { it.uppercase() }) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Cyan)
            )
        }
    }
}

@Composable
private fun DashboardScreen(vm: MainViewModel) {
    LaunchedEffect(Unit) { vm.refresh() }
    val machines = vm.data.machines
    val healthy = machines.count { (it.healthScore ?: 0.0) >= 70 }
    val attention = machines.count { val h = it.healthScore ?: 0.0; h in 40.0..69.999 }
    val critical = machines.count { (it.healthScore ?: 0.0) < 40 }
    val avg = machines.mapNotNull { it.healthScore }.let { if (it.isEmpty()) 0.0 else it.average() }
    val urgent = vm.data.alerts.count { it.severity?.lowercase() in setOf("critical", "high") && !it.resolved }
    val openOrders = vm.data.workOrders.count { it.status?.lowercase() != "completed" }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MAINTAIN AI", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Predictive maintenance intelligence", color = TextMuted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Green))
                        Spacer(Modifier.width(6.dp))
                        Text("LIVE", color = Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Text("Auto-sync", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (vm.error != null) item { ErrorCard(vm.error!!) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (vm.loading) "Connecting…" else "System operational", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(vm.lastUpdated.ifEmpty { "Syncing" }, color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Fleet health", color = TextMuted)
                    Text(if (machines.isEmpty()) "—" else "%.0f%%".format(avg), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { (avg / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                        color = if (avg >= 70) Green else if (avg >= 40) Amber else Red,
                        trackColor = Surface2
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Updated automatically from the MAINTAIN AI backend", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Machines", machines.size.toString(), Icons.Default.Factory, Cyan, Modifier.weight(1f))
                MetricCard("Urgent", urgent.toString(), Icons.Default.Warning, Red, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Open orders", openOrders.toString(), Icons.Default.Build, Amber, Modifier.weight(1f))
                MetricCard("Healthy", healthy.toString(), Icons.Default.CheckCircle, Green, Modifier.weight(1f))
            }
        }
        item {
            SectionTitle("Health distribution", "Fleet condition at a glance")
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    HealthPie(healthy, attention, critical, Modifier.size(138.dp))
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LegendRow("Healthy", healthy, Green, machines.size)
                        LegendRow("Attention", attention, Amber, machines.size)
                        LegendRow("Critical", critical, Red, machines.size)
                    }
                }
            }
        }
        item {
            SectionTitle("Work orders", "Latest maintenance activity")
            Spacer(Modifier.height(8.dp))
            if (vm.data.workOrders.isEmpty()) {
                EmptyState("No work orders", "New maintenance jobs will appear automatically.")
            } else {
                vm.data.workOrders.take(3).forEach { WorkOrderPreview(it, machines) }
            }
        }
        item {
            SectionTitle("AI model intelligence", "Latest predictive-maintenance output")
            Spacer(Modifier.height(8.dp))
            if (vm.data.aiInsights.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10283A)), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Cyan, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("AI insights ready", fontWeight = FontWeight.Bold)
                            Text("Predictive model results will appear automatically when the model service publishes them.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                vm.data.aiInsights.take(3).forEach { AiInsightCard(it) }
            }
        }
        item { SectionTitle("Machines", "Live machine health status") }
        if (machines.isEmpty()) item { EmptyState("No machine data", "Connect to the MAINTAIN AI backend to see your fleet.") }
        items(machines, key = { it.id }) { MachineCard(it) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(title, color = TextMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun WorkOrderPreview(order: WorkOrder, machines: List<Machine>) {
    val machine = machines.firstOrNull { it.id == order.machineId }
    val priority = order.priority?.uppercase() ?: "MEDIUM"
    val color = if (priority == "CRITICAL" || priority == "HIGH") Red else Amber
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Build, null, tint = Cyan)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("WO-${order.id} • ${machine?.name ?: "Machine #${order.machineId}"}", fontWeight = FontWeight.Bold)
                Text(order.problem ?: "Maintenance work order", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                Text(order.status?.replace('_', ' ')?.uppercase() ?: "UNKNOWN", color = TextMuted, style = MaterialTheme.typography.labelSmall)
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
            drawArc(color, start, sweep, false, style = Stroke(width = 26f, cap = StrokeCap.Butt))
            start += sweep
        }
        drawCircle(Surface, radius = size.minDimension * .26f)
    }
}

@Composable
private fun LegendRow(label: String, count: Int, color: Color, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, color = TextMuted, modifier = Modifier.width(72.dp))
        Text(count.toString(), fontWeight = FontWeight.Bold)
        if (total > 0) Text("  ${count * 100 / total}%", color = TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MachineCard(m: Machine) {
    val health = m.healthScore
    val status = when { health == null -> "NO DATA"; health >= 70 -> "HEALTHY"; health >= 40 -> "ATTENTION"; else -> "CRITICAL" }
    val accent = when (status) { "HEALTHY" -> Green; "ATTENTION" -> Amber; "CRITICAL" -> Red; else -> TextMuted }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Surface2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PrecisionManufacturing, null, tint = Cyan)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, fontWeight = FontWeight.SemiBold)
                    Text(m.machineCode ?: "No machine code", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                MiniChip(status, accent)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Health", color = TextMuted, modifier = Modifier.width(58.dp))
                LinearProgressIndicator(progress = { ((health ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape), color = accent, trackColor = Surface2)
                Spacer(Modifier.width(10.dp))
                Text(health?.let { "%.0f%%".format(it) } ?: "—", fontWeight = FontWeight.Bold)
            }
            if (!m.location.isNullOrBlank() || m.operatingHours != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!m.location.isNullOrBlank()) InfoText(Icons.Default.LocationOn, m.location!!)
                    if (m.operatingHours != null) InfoText(Icons.Default.Schedule, "%.0f h".format(m.operatingHours))
                }
            }
        }
    }
}

@Composable
private fun InfoText(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AiInsightCard(i: AiModelInsight) {
    val risk = i.riskScore ?: 0.0
    val accent = if (risk >= 70) Red else if (risk >= 40) Amber else Green
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = Cyan)
                Spacer(Modifier.width(8.dp))
                Text(i.machineName ?: "Machine", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(i.modelVersion ?: "AI", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(10.dp))
            i.diagnosis?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniChip("Risk ${"%.0f".format(risk)}%", accent)
                i.confidence?.let { MiniChip("Confidence ${"%.0f".format(if (it <= 1) it * 100 else it)}%", Cyan) }
                i.anomalyScore?.let { MiniChip("Anomaly ${"%.0f".format(if (it <= 1) it * 100 else it)}%", Amber) }
            }
            i.recommendedAction?.let { Spacer(Modifier.height(10.dp)); Text("Recommended: $it", color = TextMuted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun MiniChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(50)) {
        Text(text, color = color, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AlertsScreen(alerts: List<Alert>) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Alerts", "Maintenance events requiring attention") }
        if (alerts.isEmpty()) item { EmptyState("All clear", "No alerts were returned by the backend.") }
        items(alerts, key = { it.id }) { AlertCard(it) }
    }
}

@Composable
private fun AlertCard(a: Alert) {
    val severity = a.severity?.uppercase() ?: "UNKNOWN"
    val accent = when (severity) { "CRITICAL", "HIGH" -> Red; "MEDIUM" -> Amber; else -> Cyan }
    val state = if (a.resolved) "RESOLVED" else if (a.acknowledged) "ACKNOWLEDGED" else "OPEN"
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(if (severity == "CRITICAL" || severity == "HIGH") Icons.Default.Warning else Icons.Default.Info, null, tint = accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.alertType ?: "Maintenance alert", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    MiniChip(severity, accent)
                }
                Spacer(Modifier.height(6.dp))
                Text(a.message ?: "No additional information.", color = TextMuted)
                Spacer(Modifier.height(7.dp))
                Text(state, color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AnalyticsScreen(machines: List<Machine>) {
    val healthy = machines.count { (it.healthScore ?: 0.0) >= 70 }
    val attention = machines.count { val h = it.healthScore ?: 0.0; h in 40.0..69.999 }
    val critical = machines.count { (it.healthScore ?: 0.0) < 40 }
    val avg = machines.mapNotNull { it.healthScore }.let { if (it.isEmpty()) 0.0 else it.average() }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("Analytics", "Fleet-level maintenance intelligence") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    HealthPie(healthy, attention, critical, Modifier.size(150.dp))
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text("Average health", color = TextMuted)
                        Text("%.1f%%".format(avg), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("${machines.size} monitored assets", color = TextMuted)
                    }
                }
            }
        }
        item { Text("Condition breakdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Breakdown("Healthy", healthy, machines.size, Green)
            Breakdown("Attention", attention, machines.size, Amber)
            Breakdown("Critical", critical, machines.size, Red)
        }
        item { Text("Operating hours", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(machines.sortedByDescending { it.operatingHours ?: 0.0 }.take(5), key = { it.id }) { m ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, Modifier.weight(1f))
                Text(m.operatingHours?.let { "%.0f h".format(it) } ?: "—", color = TextMuted)
            }
        }
    }
}

@Composable
private fun Breakdown(label: String, count: Int, total: Int, color: Color) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Row { Text(label, Modifier.weight(1f)); Text("$count", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { if (total == 0) 0f else count.toFloat() / total }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = color, trackColor = Surface2)
    }
}

@Composable
private fun ReportsScreen(data: DashboardData) {
    val avg = data.machines.mapNotNull { it.healthScore }.let { if (it.isEmpty()) 0.0 else it.average() }
    val openAlerts = data.alerts.count { !it.resolved }
    val openOrders = data.workOrders.count { it.status?.lowercase() != "completed" }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("Reports", "Management-ready maintenance overview") }
        item { ReportHero(data.machines.size, avg, openAlerts, openOrders) }
        item { ReportRow("Fleet health", if (data.machines.isEmpty()) "No data" else "%.1f%% average".format(avg), Icons.Default.Favorite) }
        item { ReportRow("Open alerts", openAlerts.toString(), Icons.Default.Notifications) }
        item { ReportRow("Open work orders", openOrders.toString(), Icons.Default.Build) }
        item { ReportRow("AI insights", data.aiInsights.size.toString(), Icons.Default.AutoAwesome) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Current work order activity", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (data.workOrders.isEmpty()) Text("No work orders currently reported.", color = TextMuted)
                    data.workOrders.take(5).forEach { order ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("WO-${order.id}", color = Cyan, style = MaterialTheme.typography.labelSmall)
                                Text(order.problem ?: "Maintenance work order", fontWeight = FontWeight.SemiBold)
                            }
                            Text(order.status?.replace('_', ' ')?.uppercase() ?: "UNKNOWN", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Predictive intelligence", fontWeight = FontWeight.Bold)
                    Text("When the trained local model publishes results, this mobile client will surface risk, anomaly score, confidence, failure window and recommended action without requiring a manual refresh.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ReportHero(machines: Int, avg: Double, alerts: Int, workOrders: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Maintenance snapshot", color = TextMuted)
            Text(if (machines == 0) "—" else "%.0f%%".format(avg), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text("overall fleet health", color = TextMuted)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$machines machines")
                Text("$alerts alerts")
                Text("$workOrders orders")
            }
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Cyan)
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f))
            Text(value, color = TextMuted)
        }
    }
}

@Composable
private fun SettingsScreen(url: String, onSave: (String) -> Unit) {
    var text by remember(url) { mutableStateOf(url) }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("Settings", "Connection and mobile monitoring preferences") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Backend connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(text, { text = it }, label = { Text("Server URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onSave(text) }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CloudDone, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save & connect")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Default: https://maintain-ai-3.vercel.app/\nLocal phone: use your computer's LAN IP with port 8000.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Automatic monitoring", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Background sync runs continuously while the app is open. New machines, alerts, work orders and AI results appear without manually refreshing the screen.", color = TextMuted)
                }
            }
        }
        item { Text("MAINTAIN AI  •  Android 0.1.0", color = TextMuted, style = MaterialTheme.typography.labelSmall) }
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
private fun EmptyState(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CloudOff, null, tint = TextMuted, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF321820)), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudOff, null, tint = Red)
            Spacer(Modifier.width(10.dp))
            Text("Connection problem: $message", color = Color(0xFFFFB8C0), style = MaterialTheme.typography.bodySmall)
        }
    }
}
