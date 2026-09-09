package com.dmjgroup.maintainai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.dmjgroup.maintainai.data.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
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
}

class MainViewModel : ViewModel() {
    var data by mutableStateOf(DashboardData()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var serverUrl by mutableStateOf(DEFAULT_SERVER_URL); private set

    fun setServerUrl(url: String) { serverUrl = url.trim().ifEmpty { DEFAULT_SERVER_URL } }

    fun refresh() = viewModelScope.launch {
        loading = true
        error = null
        runCatching { MaintainRepository().load(serverUrl) }
            .onSuccess { data = it }
            .onFailure { error = it.message ?: "Unable to connect" }
        loading = false
    }
}

@Composable
fun MaintainApp(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(bottomBar = { BottomNav(nav) }) { padding ->
            NavHost(nav, startDestination = "dashboard", modifier = Modifier.padding(padding)) {
                composable("dashboard") { DashboardScreen(vm) }
                composable("alerts") { AlertsScreen(vm.data.alerts) }
                composable("analytics") { AnalyticsScreen(vm.data.machines) }
                composable("reports") { ReportsScreen(vm.data) }
                composable("settings") { SettingsScreen(vm.serverUrl) { vm.setServerUrl(it); vm.refresh() } }
            }
        }
    }
}

@Composable private fun BottomNav(nav: NavHostController) {
    val items = listOf(
        "dashboard" to Icons.Default.Dashboard,
        "alerts" to Icons.Default.Notifications,
        "analytics" to Icons.Default.Insights,
        "reports" to Icons.Default.Assessment,
        "settings" to Icons.Default.Settings
    )
    NavigationBar {
        items.forEach { (route, icon) ->
            NavigationBarItem(
                selected = false,
                onClick = { nav.navigate(route) { launchSingleTop = true } },
                icon = { Icon(icon, null) },
                label = { Text(route.replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}

@Composable private fun DashboardScreen(vm: MainViewModel) {
    LaunchedEffect(Unit) { vm.refresh() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("MAINTAIN AI", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Maintenance companion", style = MaterialTheme.typography.bodyLarge) }
        if (vm.error != null) item { Card { Text("Connection error: ${vm.error}", Modifier.padding(16.dp)) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Machines", vm.data.machines.size.toString(), Modifier.weight(1f))
            StatCard("Critical", vm.data.machines.count { (it.healthScore ?: 0.0) < 40 }.toString(), Modifier.weight(1f))
        }}
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Alerts", vm.data.alerts.size.toString(), Modifier.weight(1f))
            StatCard("Work orders", vm.data.workOrders.size.toString(), Modifier.weight(1f))
        }}
        item { Button(onClick = vm::refresh, modifier = Modifier.fillMaxWidth()) { if (vm.loading) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Refresh data") } }
        item { Text("Machine health", style = MaterialTheme.typography.titleLarge) }
        items(vm.data.machines) { machine -> MachineCard(machine) }
    }
}

@Composable private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(14.dp)) { Text(value, style = MaterialTheme.typography.headlineSmall); Text(title) } }
}

@Composable private fun MachineCard(m: Machine) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(m.name, style = MaterialTheme.typography.titleMedium)
            Text(m.machineCode ?: "No code")
            Text("Health: ${m.healthScore?.let { "%.0f".format(it) } ?: "—"}%  •  ${m.status ?: "unknown"}")
        }
    }
}

@Composable private fun AlertsScreen(alerts: List<Alert>) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Alerts", style = MaterialTheme.typography.headlineMedium) }
        if (alerts.isEmpty()) item { Text("No alerts available") }
        items(alerts) { a ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(a.title ?: "Alert", style = MaterialTheme.typography.titleMedium)
                    Text(a.message ?: "No message")
                    Text("${a.severity ?: "unknown"} • ${a.status ?: "unknown"}")
                    if (a.createdAt != null) Text(a.createdAt, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable private fun AnalyticsScreen(machines: List<Machine>) {
    val avg = machines.mapNotNull { it.healthScore }.let { if (it.isEmpty()) 0.0 else it.average() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Analytics", style = MaterialTheme.typography.headlineMedium) }
        item { StatCard("Average health", "%.1f%%".format(avg)) }
        item { Text("Health distribution", style = MaterialTheme.typography.titleLarge) }
        item { Text("Healthy (≥70): ${machines.count { (it.healthScore ?: 0.0) >= 70 }}") }
        item { Text("Attention (40–69): ${machines.count { val h = it.healthScore ?: 0.0; h in 40.0..69.999 }}") }
        item { Text("Critical (<40): ${machines.count { (it.healthScore ?: 0.0) < 40 }}") }
        item { Text("Machines with health data: ${machines.count { it.healthScore != null }}") }
    }
}

@Composable private fun ReportsScreen(data: DashboardData) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Reports", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Live maintenance summary", style = MaterialTheme.typography.titleLarge) }
        item { Text("Machines monitored: ${data.machines.size}") }
        item { Text("Average machine health: ${data.machines.mapNotNull { it.healthScore }.let { if (it.isEmpty()) "—" else "%.1f%%".format(it.average()) }}") }
        item { Text("Alerts returned by backend: ${data.alerts.size}") }
        item { Text("Work orders returned by backend: ${data.workOrders.size}") }
        item { Text("This companion app reads the same MAINTAIN AI backend used by the desktop application.") }
    }
}

@Composable private fun SettingsScreen(url: String, onSave: (String) -> Unit) {
    var text by remember(url) { mutableStateOf(url) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Backend URL", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { onSave(text) }, modifier = Modifier.fillMaxWidth()) { Text("Save server URL") }
        Text("Default: https://maintain-ai-3.vercel.app/\nFor a local physical-phone test, use the computer's LAN IP and port 8000.")
    }
}
