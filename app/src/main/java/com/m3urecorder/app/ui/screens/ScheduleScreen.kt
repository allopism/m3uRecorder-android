package com.m3urecorder.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3urecorder.app.data.*
import com.m3urecorder.app.ui.components.*
import com.m3urecorder.app.ui.theme.*
import com.m3urecorder.app.util.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun ScheduleTab(
    url: String,
    onUrlChange: (String) -> Unit,
    startDt: LocalDateTime,
    onStartDtChange: (LocalDateTime) -> Unit,
    endDt: LocalDateTime,
    onEndDtChange: (LocalDateTime) -> Unit,
    filename: String,
    onFilenameChange: (String) -> Unit,
    format: String,
    onFormatChange: (String) -> Unit,
    vpnCountry: String,
    onVpnCountryChange: (String) -> Unit,
    vpnCountries: VpnCountriesResponse,
    schedules: List<ScheduleItem>,
    onSaveSchedule: () -> Unit,
    onRefreshSchedules: () -> Unit,
    onClearFinished: () -> Unit,
    onCancelJob: (String) -> Unit,
    onDeleteJob: (String) -> Unit,
    onStopActive: () -> Unit,
    onPlayJob: (String) -> Unit
) {
    val context = LocalContext.current
    val dtFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm") }
    val durationSecs = max(0L, Duration.between(startDt, endDt).seconds).toInt()

    fun pickDateTime(initial: LocalDateTime, onPicked: (LocalDateTime) -> Unit) {
        DatePickerDialog(
            context,
            { _, y, m, d ->
                TimePickerDialog(
                    context,
                    { _, hh, mm ->
                        onPicked(LocalDateTime.of(y, m + 1, d, hh, mm, 0))
                    },
                    initial.hour,
                    initial.minute,
                    true
                ).show()
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BgCard,
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("📅 Programar nueva grabación", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    OutlinedTextField(
                        value = url,
                        onValueChange = onUrlChange,
                        label = { Text("🔗 Enlace del Stream") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Selectores nativos de Inicio y Fin
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { pickDateTime(startDt, onStartDtChange) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("🕐 Inicio", fontSize = 11.sp, color = TextSecondary)
                                Text(startDt.format(dtFormatter), fontSize = 12.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = { pickDateTime(endDt, onEndDtChange) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("🕑 Fin", fontSize = 11.sp, color = TextSecondary)
                                Text(endDt.format(dtFormatter), fontSize = 12.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Chips rápidos para añadir duración a la hora de inicio
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(30, 60, 90, 120, 180, 240).forEach { mins ->
                            val lbl = if (mins % 60 == 0) "+${mins / 60}h" else "+${mins}m"
                            AssistChip(
                                onClick = { onEndDtChange(startDt.plusMinutes(mins.toLong())) },
                                label = { Text(lbl, fontSize = 12.sp) }
                            )
                        }
                    }

                    // Previsualización de duración
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgInput,
                        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏱️ Duración programada:", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = secsToHHMMSS(durationSecs),
                                color = Color(0xFF60A5FA),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    OutlinedTextField(
                        value = filename,
                        onValueChange = onFilenameChange,
                        label = { Text("📝 Nombre del archivo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    VpnCountrySelectorField(
                        selectedCountry = vpnCountry,
                        onSelectCountry = onVpnCountryChange,
                        vpnCountries = vpnCountries
                    )

                    FormatSelectorRow(selectedFormat = format, onSelectFormat = onFormatChange)

                    Button(
                        onClick = onSaveSchedule,
                        enabled = url.isNotBlank() && durationSecs > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text("📅 Guardar Programación", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Cabecera de Cola de Programadas
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📋 Cola programada (${schedules.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onRefreshSchedules) { Text("🔄 Actualizar", fontSize = 12.sp) }
                    TextButton(onClick = onClearFinished) { Text("🗑️ Limpiar", color = Color(0xFFFDA4AF), fontSize = 12.sp) }
                }
            }
        }

        if (schedules.isEmpty()) {
            item {
                Text(
                    "No hay grabaciones programadas",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(schedules, key = { it.id }) { job ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
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
                            Text(job.filename, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            StatusBadge(status = job.status)
                        }

                        Text(
                            text = "🕐 ${formatIsoDisplay(job.startTime)}  →  ${formatIsoDisplay(job.endTime)}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⏱️ ${secsToHHMMSS(job.durationSeconds)}", color = Color(0xFF93C5FD), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            if (job.vpnCountry.isNotBlank()) {
                                Text("🌍 VPN: ${job.vpnCountry}", color = Color(0xFF6EE7B7), fontSize = 12.sp)
                            }
                            if (job.filesizeHuman.isNotBlank()) {
                                Text("💾 ${job.filesizeHuman}", color = Color(0xFFA7F3D0), fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (job.status) {
                                "pending" -> {
                                    Button(
                                        onClick = { onCancelJob(job.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🚫 Cancelar", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                                "running" -> {
                                    Button(
                                        onClick = onStopActive,
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("⏹️ Detener", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                                else -> {
                                    if (job.status == "ok" || job.status == "stopped") {
                                        Button(
                                            onClick = { onPlayJob(job.filename) },
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("▶️ Ver", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { onDeleteJob(job.id) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🗑️ Borrar", color = Color(0xFFFDA4AF), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
