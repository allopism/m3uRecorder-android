package com.m3urecorder.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3urecorder.app.R
import com.m3urecorder.app.data.VpnCountriesResponse
import com.m3urecorder.app.ui.DURATION_CHOICES
import com.m3urecorder.app.ui.FORMAT_CHOICES
import com.m3urecorder.app.ui.RecordSheetState
import com.m3urecorder.app.ui.SheetMode
import com.m3urecorder.app.ui.components.AppChip
import com.m3urecorder.app.ui.components.ChannelLogo
import com.m3urecorder.app.ui.components.ChoiceChips
import com.m3urecorder.app.ui.components.IconTile
import com.m3urecorder.app.ui.theme.AppTheme
import com.m3urecorder.app.util.fmtDay
import com.m3urecorder.app.util.humanDuration
import com.m3urecorder.app.util.isRepeatedHour
import com.m3urecorder.app.util.isSkippedHour
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

/** Panel inferior para grabar ahora o programar (el mismo flujo que la web). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheet(
    state: RecordSheetState,
    vpnEnabled: Boolean,
    vpnCountries: VpnCountriesResponse,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    onWatchLive: () -> Unit
) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(state, context, onDismiss)

            if (state.isManual) {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = { state.url = it },
                    label = { Text("Enlace del stream") },
                    placeholder = { Text("https://ejemplo.com/directo/index.m3u8") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SheetMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = state.mode == m,
                        onClick = { state.mode = m; state.error = null },
                        shape = SegmentedButtonDefaults.itemShape(i, SheetMode.entries.size),
                        icon = {},
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = c.accentSoft,
                            activeContentColor = c.accent,
                            inactiveContainerColor = c.surface,
                            inactiveContentColor = c.text2
                        )
                    ) { Text(if (m == SheetMode.NOW) "Ahora" else "Programar") }
                }
            }

            if (state.mode == SheetMode.NOW) NowSection(state) else ScheduleSection(state, context)

            OutlinedTextField(
                value = state.name,
                onValueChange = { state.name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OptionsSection(state, vpnEnabled, vpnCountries)

            state.error?.let {
                Text(
                    it,
                    color = c.rec,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.recSoft, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                )
            }

            Button(
                onClick = onSubmit,
                enabled = !state.submitting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = c.onAccent, strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (state.mode == SheetMode.NOW) Icons.Rounded.FiberManualRecord else Icons.Rounded.Schedule,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(if (state.mode == SheetMode.NOW) "Empezar a grabar" else "Programar grabación")
                }
            }

            if (!state.isManual) {
                TextButton(onClick = onWatchLive, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Rounded.PlayCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Ver en directo en el móvil")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header(state: RecordSheetState, context: Context, onDismiss: () -> Unit) {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val ch = state.channel
        if (ch != null) ChannelLogo(ch.logoId, ch.name, 48.dp) else IconTile(Icons.Rounded.Link, 48.dp)
        Column(Modifier.weight(1f)) {
            Text(state.title, style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = if (ch != null) ch.group.ifBlank { "Canal" } else "Pega la dirección de un directo"
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.text2)
        }
        if (ch != null) {
            IconButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText(ch.name, ch.url))
                Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Rounded.ContentCopy, "Copiar enlace del canal", tint = c.text2) }
        }
        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Cerrar", tint = c.text2) }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.text2)
}

@Composable
private fun NowSection(state: RecordSheetState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Duración")
        ChoiceChips(DURATION_CHOICES, state.durationChoice, onSelect = { state.durationChoice = it; state.error = null })
        AnimatedVisibility(state.durationChoice == "custom") {
            OutlinedTextField(
                value = state.customDuration,
                onValueChange = { state.customDuration = it },
                label = { Text("Duración (HH:MM:SS)") },
                placeholder = { Text("01:30:00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ScheduleSection(state: RecordSheetState, context: Context) {
    val c = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateTimeBox("Empieza", state.start, Modifier.weight(1f)) {
                pickDateTime(context, state.start) { picked ->
                    // Al mover el inicio se conserva la duración elegida
                    val length = Duration.between(state.start, state.end)
                    state.start = picked
                    state.end = picked.plus(if (length.isNegative || length.isZero) Duration.ofHours(1) else length)
                }
            }
            DateTimeBox("Termina", state.end, Modifier.weight(1f)) {
                pickDateTime(context, state.end) { state.end = it }
            }
        }
        // Cambio de hora: elegir cuál de las dos veces, o avisar si la hora no existe
        HourNotice("El inicio", state.start, state.startFold) { state.startFold = it }
        HourNotice("El fin", state.end, state.endFold) { state.endFold = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5L to "En 5 min", 15L to "En 15 min", 60L to "En 1 h").forEach { (mins, label) ->
                AppChip(label, selected = false, onClick = {
                    val length = Duration.between(state.start, state.end)
                    state.start = LocalDateTime.now().plusMinutes(mins).withSecond(0).withNano(0)
                    state.end = state.start.plus(if (length.isNegative || length.isZero) Duration.ofHours(1) else length)
                })
            }
        }
        FieldLabel("Duración")
        ChoiceChips(
            options = listOf(30L to "30 min", 60L to "1 h", 120L to "2 h", 180L to "3 h"),
            selected = state.realSeconds / 60,
            onSelect = state::setLengthMinutes
        )
        // Resumen en una frase (con la duración real, aunque haya cambio de hora en medio)
        val secs = state.realSeconds
        val summary = if (secs > 0) {
            "Graba ${humanDuration(secs)} · ${fmtDay(state.start)} de ${state.start.format(TIME_FMT)} a ${state.end.format(TIME_FMT)}"
        } else {
            "La hora de fin tiene que ser posterior a la de inicio"
        }
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = if (secs > 0) c.text2 else c.rec,
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface2, RoundedCornerShape(10.dp))
                .padding(10.dp)
        )
    }
}

/**
 * Aviso del cambio de hora: si la hora se repite (octubre) deja elegir la 1.ª o la 2.ª vez,
 * como la web; si no existe (marzo), avisa de que se adelanta una hora.
 */
@Composable
private fun HourNotice(which: String, value: LocalDateTime, fold: Int, onFold: (Int) -> Unit) {
    val c = AppTheme.colors
    val time = value.format(TIME_FMT)
    when {
        isRepeatedHour(value) -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.warnSoft, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "$which (las $time) ocurre dos veces por el cambio de hora. ¿Cuál?",
                style = MaterialTheme.typography.bodySmall,
                color = c.text
            )
            ChoiceChips(
                options = listOf(0 to "1.ª vez (verano)", 1 to "2.ª vez (invierno)"),
                selected = fold,
                onSelect = onFold
            )
        }
        isSkippedHour(value) -> Text(
            "$which (las $time) no existe ese día por el cambio de hora: se adelantará una hora.",
            style = MaterialTheme.typography.bodySmall,
            color = c.warn,
            modifier = Modifier
                .fillMaxWidth()
                .background(c.warnSoft, RoundedCornerShape(10.dp))
                .padding(10.dp)
        )
    }
}

@Composable
private fun DateTimeBox(label: String, value: LocalDateTime, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .border(1.dp, c.borderStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.text3)
        Text(value.format(TIME_FMT), style = MaterialTheme.typography.titleLarge, color = c.text)
        Text(fmtDay(value), style = MaterialTheme.typography.bodySmall, color = c.text2)
    }
}

private fun pickDateTime(context: Context, initial: LocalDateTime, onPicked: (LocalDateTime) -> Unit) {
    // Con el color de la app (claro u oscuro según el sistema)
    DatePickerDialog(context, R.style.Theme_M3URecorder_Picker, { _, y, m, d ->
        TimePickerDialog(context, R.style.Theme_M3URecorder_Picker, { _, hh, mm ->
            onPicked(LocalDateTime.of(y, m + 1, d, hh, mm, 0))
        }, initial.hour, initial.minute, true).show()
    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

/** Formato y país de la VPN, plegados (la mayoría de veces no hace falta tocarlos). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsSection(state: RecordSheetState, vpnEnabled: Boolean, vpnCountries: VpnCountriesResponse) {
    val c = AppTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val formatLabel = state.format.uppercase()
    val vpnLabel = state.vpnCountry.ifBlank { "VPN actual" }
    Column {
        HorizontalDivider(color = c.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.ExpandMore, null, tint = c.text2,
                modifier = Modifier.rotate(if (open) 180f else 0f)
            )
            Spacer(Modifier.size(8.dp))
            Text("Opciones", style = MaterialTheme.typography.labelLarge, color = c.text)
            Spacer(Modifier.weight(1f))
            Text(
                if (vpnEnabled) "$formatLabel · $vpnLabel" else formatLabel,
                style = MaterialTheme.typography.bodySmall,
                color = c.text3
            )
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                Dropdown(
                    label = "Formato",
                    value = FORMAT_CHOICES.firstOrNull { it.first == state.format }?.second ?: formatLabel,
                    options = FORMAT_CHOICES,
                    onSelect = { state.format = it }
                )
                if (vpnEnabled) {
                    val countries = listOf("" to "El país actual de la VPN") +
                        vpnCountries.featured.map { it.value to it.label } +
                        vpnCountries.others.map { it to it }
                    Dropdown(
                        label = "País de la VPN",
                        value = countries.firstOrNull { it.first == state.vpnCountry }?.second ?: state.vpnCountry,
                        options = countries,
                        onSelect = { state.vpnCountry = it }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (v, text) ->
                DropdownMenuItem(
                    text = { Text(text, color = if (text.isEmpty()) Color.Unspecified else AppTheme.colors.text) },
                    onClick = {
                        onSelect(v)
                        expanded = false
                    }
                )
            }
        }
    }
}
