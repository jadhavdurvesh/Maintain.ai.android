package com.dmjgroup.maintainai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmjgroup.maintainai.data.Machine
import com.dmjgroup.maintainai.data.WorkOrder

private val WorkSurface = Color(0xFF101C2D)
private val WorkSurface2 = Color(0xFF16243A)
private val WorkCyan = Color(0xFF55D6FF)
private val WorkGreen = Color(0xFF45D483)
private val WorkAmber = Color(0xFFFFC857)
private val WorkRed = Color(0xFFFF5C70)
private val WorkMuted = Color(0xFF8FA1B8)

@Composable
fun WorkOrdersScreen(workOrders: List<WorkOrder>, machines: List<Machine>) {
    val machineNames = machines.associateBy { it.id }
    LazyColumn(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Work Orders", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Live maintenance jobs and assigned actions", color = WorkMuted)
            }
        }
        if (workOrders.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = WorkSurface), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Build, null, tint = WorkMuted, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No work orders", fontWeight = FontWeight.Bold)
                        Text("New maintenance jobs will appear here automatically.", color = WorkMuted)
                    }
                }
            }
        }
        items(workOrders, key = { it.id }) { order ->
            val machine = machineNames[order.machineId]
            WorkOrderCard(order, machine?.name ?: "Machine #${order.machineId}")
        }
    }
}

@Composable
private fun WorkOrderCard(order: WorkOrder, machineName: String) {
    val status = order.status?.replace('_', ' ')?.uppercase() ?: "UNKNOWN"
    val priority = order.priority?.uppercase() ?: "MEDIUM"
    val priorityColor = when (priority) {
        "CRITICAL", "HIGH" -> WorkRed
        "MEDIUM" -> WorkAmber
        else -> WorkGreen
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WorkSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, null, tint = WorkCyan)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("WO-${order.id}", color = WorkMuted, style = MaterialTheme.typography.labelSmall)
                    Text(machineName, fontWeight = FontWeight.Bold)
                }
                Surface(color = priorityColor.copy(alpha = .13f), shape = RoundedCornerShape(50)) {
                    Text(priority, color = priorityColor, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(order.problem ?: "Maintenance work order", style = MaterialTheme.typography.titleMedium)
            order.recommendedActions?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = WorkMuted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(status, when {
                    status.contains("COMPLETED") -> WorkGreen
                    status.contains("PROGRESS") -> WorkCyan
                    status.contains("PENDING") -> WorkAmber
                    else -> WorkMuted
                })
                order.assignedTo?.takeIf { it.isNotBlank() }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = WorkMuted, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(it, color = WorkMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                order.createdAt?.takeIf { it.isNotBlank() }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = WorkMuted, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(it.replace('T', ' ').take(16), color = WorkMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(50)) {
        Text(text, color = color, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
