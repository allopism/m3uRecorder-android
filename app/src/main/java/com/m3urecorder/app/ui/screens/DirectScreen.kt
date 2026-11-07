package com.m3urecorder.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3urecorder.app.data.*
import com.m3urecorder.app.ui.components.*
import com.m3urecorder.app.ui.theme.*
import com.m3urecorder.app.util.*

@Composable
fun DirectRecordTab(
    url: String,
    onUrlChange: (String) -> Unit,
    duration: String,
    onDurationChange: (String) -> Unit,
    filename: String,
    onFilenameChange: (String) -> Unit,
    format: String,
    onFormatChange: (String) -> Unit,
    vpnCountry: String,
    onVpnCountryChange: (String) -> Unit,
    vpnCountries: VpnCountriesResponse,
    isRunning: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = BgCard,
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("⚡ Nueva grabación al vuelo (-c copy)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text("🔗 Enlace del Stream (M3U8 / TS / HTTP)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = duration,
                    onValueChange = onDurationChange,
                    label = { Text("⏱️ Duración (HH:MM:SS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Chips rápidos de duración
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "15m" to "00:15:00",
                        "30m" to "00:30:00",
                        "1h" to "01:00:00",
                        "1h 30m" to "01:30:00",
                        "2h" to "02:00:00",
                        "3h" to "03:00:00",
                        "4h" to "04:00:00"
                    ).forEach { (label, valStr) ->
                        AssistChip(
                            onClick = { onDurationChange(valStr) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                // Selector de país VPN + Chips rápidos (España, Japón, Suiza, Países Bajos)
                VpnCountrySelectorField(
                    selectedCountry = vpnCountry,
                    onSelectCountry = onVpnCountryChange,
                    vpnCountries = vpnCountries
                )

                // Nombre de archivo + botón actualizar hora
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filename,
                        onValueChange = onFilenameChange,
                        label = { Text("📝 Nombre del archivo") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = { onFilenameChange(defaultTimestampFilename("grabacion")) }
                    ) {
                        Text("🔄 Hora")
                    }
                }

                // Selector de Formato (MP4, MKV, TS)
                FormatSelectorRow(selectedFormat = format, onSelectFormat = onFormatChange)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStartRecording,
                        enabled = !isRunning && url.isNotBlank(),
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text("▶️ Iniciar Grabación", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    if (isRunning) {
                        Button(
                            onClick = onStopRecording,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
                        ) {
                            Text("⏹️ Parar", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
