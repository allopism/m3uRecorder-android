package com.m3urecorder.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3urecorder.app.data.*
import com.m3urecorder.app.ui.theme.*
import com.m3urecorder.app.util.*
import java.time.OffsetDateTime
import kotlin.math.max
import kotlin.math.min

// Piezas reutilizadas por varias pantallas.

@Composable
fun LiveStatusBanner(
    status: ServerStatus,
    onStopClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    val bg = if (status.running) Color(0xFF4C0519).copy(alpha = 0.65f) else BgCard
    val border = if (status.running) AccentRose.copy(alpha = 0.6f) else Color(0xFF334155)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (status.running) {
                        Text("🔴", fontSize = 14.sp)
                    }
                    Text(
                        text = status.progress.ifBlank { "Listo para grabar" },
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (status.running) {
                    Button(
                        onClick = onStopClick,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("⏹️ Detener", fontSize = 11.5.sp, color = Color.White)
                    }
                } else {
                    TextButton(
                        onClick = onDismissClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("✕ Ocultar", fontSize = 11.5.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (status.running && status.durationRequested != null && status.startTime != null) {
                val totalSec = max(1, status.durationRequested)
                val elapsedSec = runCatching {
                    val startInst = OffsetDateTime.parse(status.startTime).toInstant()
                    max(0L, (System.currentTimeMillis() / 1000L) - startInst.epochSecond).toInt()
                }.getOrDefault(0)
                val progress = min(1f, elapsedSec.toFloat() / totalSec.toFloat())

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = AccentBlue,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
                Text(
                    text = "${secsToHHMMSS(elapsedSec)} / ${secsToHHMMSS(totalSec)} (${(progress * 100).toInt()}%)",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun VpnCountrySelectorField(
    selectedCountry: String,
    onSelectCountry: (String) -> Unit,
    vpnCountries: VpnCountriesResponse
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                val txt = if (selectedCountry.isBlank()) {
                    "🛡️ País VPN: Usar país actual (Sin cambiar)"
                } else {
                    "🌍 País VPN para esta grabación: $selectedCountry"
                }
                Text(txt, color = Color(0xFFA7F3D0), fontSize = 12.5.sp)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(BgCard)
            ) {
                DropdownMenuItem(
                    text = { Text("🛡️ Usar país actual de la VPN", color = Color.White) },
                    onClick = {
                        expanded = false
                        onSelectCountry("")
                    }
                )
                vpnCountries.featured.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c.label, color = Color.White) },
                        onClick = {
                            expanded = false
                            onSelectCountry(c.value)
                        }
                    )
                }
            }
        }

        // Chips rápidos (España, Japón, Suiza, Países Bajos)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "🇪🇸 España" to "Spain",
                "🇯🇵 Japón" to "Japan",
                "🇨🇭 Suiza" to "Switzerland",
                "🇳🇱 Países Bajos" to "Netherlands",
                "🛡️ Actual" to ""
            ).forEach { (lbl, code) ->
                FilterChip(
                    selected = selectedCountry == code,
                    onClick = { onSelectCountry(code) },
                    label = { Text(lbl, fontSize = 11.5.sp) }
                )
            }
        }
    }
}

@Composable
fun FormatSelectorRow(
    selectedFormat: String,
    onSelectFormat: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📦 Contenedor:", color = TextSecondary, fontSize = 12.5.sp)
        listOf("mkv" to "MKV (Multipista)", "mp4" to "MP4", "ts" to "TS").forEach { (ext, label) ->
            FilterChip(
                selected = selectedFormat == ext,
                onClick = { onSelectFormat(ext) },
                label = { Text(label, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
fun StatMiniCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = BgCard,
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, color = TextSecondary, fontSize = 11.sp)
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (text, color) = when (status) {
        "ok" -> "✅ Completada" to AccentEmerald
        "running" -> "🔴 Grabando" to AccentRose
        "pending" -> "🕐 Pendiente" to AccentBlue
        "stopped" -> "⏹️ Detenida" to AccentAmber
        "cancelled" -> "🚫 Cancelada" to TextSecondary
        else -> "❌ Fallida" to AccentRose
    }
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
internal fun FastActionBtn(
    text: String,
    bgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
