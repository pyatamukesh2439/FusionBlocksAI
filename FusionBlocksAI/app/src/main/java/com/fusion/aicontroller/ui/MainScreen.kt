package com.fusion.aicontroller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fusion.aicontroller.model.ControllerStatus
import com.fusion.aicontroller.model.LogEntry

@Composable
fun MainScreen(
    status: ControllerStatus,
    placements: Int,
    logs: List<LogEntry>,
    hasOverlayPermission: Boolean,
    onGrantOverlayClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Fusion Blocks AI",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Intelligent Line-Clearing Puzzle Solver",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (status == ControllerStatus.RUNNING) Color(0xFF00B0FF) else Color(0xFF424242)
                ) {
                    Text(
                        text = status.displayName,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!hasOverlayPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF332020))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Permission Required", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("Display Over Other Apps permission is required.", fontSize = 12.sp, color = Color.LightGray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onGrantOverlayClick) { Text("Grant Permission") }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartClick,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                    enabled = status != ControllerStatus.RUNNING
                ) {
                    Text("START", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = onStopClick,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                    enabled = status == ControllerStatus.RUNNING
                ) {
                    Text("STOP", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Puzzle Statistics", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Intelligent Placements Made: $placements", color = Color.White)
                }
            }

            Text("Activity Log", style = MaterialTheme.typography.titleMedium, color = Color.White)

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181818))
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), reverseLayout = true) {
                    items(logs.reversed()) { log ->
                        Text(
                            text = "[${log.timestamp}] ${log.message}",
                            fontSize = 11.sp,
                            color = Color(0xFFB0BEC5),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
