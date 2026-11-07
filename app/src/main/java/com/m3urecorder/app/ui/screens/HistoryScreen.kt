package com.m3urecorder.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3urecorder.app.data.*
import com.m3urecorder.app.ui.components.*
import com.m3urecorder.app.ui.theme.*
import com.m3urecorder.app.util.*

@Composable
fun HistoryTab(
    history: List<HistoryItem>,
    onRefresh: () -> Unit,
    onPlayRecording: (HistoryItem) -> Unit,
    onDownloadRecording: (HistoryItem) -> Unit,
    onDeleteRecording: (HistoryItem) -> Unit
) {
    val totalOk = history.count { it.status == "ok" || it.status == "stopped" }
    val totalBytes = history.sumOf { it.filesizeBytes }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Resumen de Estadísticas
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMiniCard("Grabaciones", "${history.size}", Color.White, Modifier.weight(1f))
                StatMiniCard("Completadas", "$totalOk", AccentEmerald, Modifier.weight(1f))
                StatMiniCard("Espacio", formatBytesHuman(totalBytes), Color(0xFF60A5FA), Modifier.weight(1f))
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📜 Grabaciones guardadas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextButton(onClick = onRefresh) { Text("🔄 Recargar", fontSize = 12.sp) }
            }
        }

        if (history.isEmpty()) {
            item {
                Text(
                    "Aún no hay grabaciones en el historial",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(history, key = { it.filename + it.start }) { item ->
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = BgCard,
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.filename,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            StatusBadge(status = item.status)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("⏱️ ${secsToHHMMSS(item.mediaDurationSeconds)}", color = Color(0xFF93C5FD), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Text("💾 ${item.filesizeHuman}", color = Color(0xFFA7F3D0), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            if (item.vpnCountry.isNotBlank()) {
                                Text("🌍 ${item.vpnCountry}", color = Color(0xFF6EE7B7), fontSize = 12.sp)
                            }
                        }

                        Text(
                            text = "📅 ${formatIsoDisplay(item.start)} · ${if (item.type == "scheduled") "Programada" else "Directo"}",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (item.filesizeBytes > 0) {
                                Button(
                                    onClick = { onPlayRecording(item) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                    modifier = Modifier.weight(2f)
                                ) {
                                    Text("▶️ Ver aquí", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                }

                                FilledTonalButton(
                                    onClick = { onDownloadRecording(item) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("⬇️", fontSize = 13.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = { onDeleteRecording(item) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🗑️", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
