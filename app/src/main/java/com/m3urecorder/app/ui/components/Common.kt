package com.m3urecorder.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3urecorder.app.data.ServerStatus
import com.m3urecorder.app.ui.theme.AppTheme
import com.m3urecorder.app.util.epochMillis
import com.m3urecorder.app.util.shortDuration
import com.m3urecorder.app.util.stripExt
import kotlinx.coroutines.delay

/** Hora actual que se actualiza cada segundo (para cuentas atrás y progreso). */
@Composable
fun rememberNow(periodMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

enum class TagStyle { NEUTRAL, REC, WARN, OK, ACCENT }

/** Etiqueta pequeña de estado ("Grabando", "Fallida"…). */
@Composable
fun Tag(text: String, style: TagStyle = TagStyle.NEUTRAL) {
    val c = AppTheme.colors
    val (fg, bg) = when (style) {
        TagStyle.NEUTRAL -> c.text2 to c.surface2
        TagStyle.REC -> c.rec to c.recSoft
        TagStyle.WARN -> c.warn to c.warnSoft
        TagStyle.OK -> c.ok to c.surface2
        TagStyle.ACCENT -> c.accent to c.accentSoft
    }
    Text(
        text,
        color = fg,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** Título de sección con texto secundario y acción opcional a la derecha. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.text)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.text3)
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

/** Mensaje centrado cuando una lista está vacía. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    val c = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = c.text3, modifier = Modifier.size(36.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.text, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.text2, textAlign = TextAlign.Center)
        action?.let {
            Spacer(Modifier.height(4.dp))
            it()
        }
    }
}

/** Fila desplazable de chips con una opción activa. */
@Composable
fun <T> ChoiceChips(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding
    ) {
        items(options, key = { it.second }) { (value, label) ->
            AppChip(label = label, selected = value == selected, onClick = { onSelect(value) })
        }
    }
}

@Composable
fun AppChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shape = RoundedCornerShape(999.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = c.surface,
            labelColor = c.text2,
            selectedContainerColor = c.accentSoft,
            selectedLabelColor = c.accent
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = c.border,
            selectedBorderColor = c.accent.copy(alpha = 0.5f)
        )
    )
}

/** Tarjeta base: superficie con borde fino y esquinas redondeadas. */
@Composable
fun cardModifier(): Modifier {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(14.dp)
    return Modifier
        .clip(shape)
        .background(c.surface)
        .border(1.dp, c.border, shape)
}

/**
 * Barra "Grabando" bajo la cabecera: logo, título, tiempo (o la fase: guardando,
 * reconectando…), país de la VPN, progreso y botón Detener.
 */
@Composable
fun LiveBar(status: ServerStatus, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val now = rememberNow()
    val title = status.channel.ifBlank { status.filename?.let(::stripExt) ?: "Grabación" }

    // El servidor manda el texto de ffmpeg; solo se muestran las fases legibles
    val progressText = status.progress.removePrefix("[Programada] ")
    val phase = if (Regex("^(Guardando|Preparando|Reconectando|🌍)").containsMatchIn(progressText)) progressText else ""
    val startMs = epochMillis(status.startTime)
    val total = status.durationRequested ?: 0
    val elapsed = startMs?.let { ((now - it) / 1000).toInt().coerceAtLeast(0) } ?: 0
    val fraction = if (total > 0) (elapsed.toFloat() / total).coerceIn(0f, 1f) else 0f

    val sub = buildList {
        add(if (status.mode == "scheduled") "Programada" else "En directo")
        when {
            phase.isNotBlank() -> add(phase)
            total > 0 -> add("${shortDuration(elapsed)} / ${shortDuration(total)}")
        }
    }.joinToString(" · ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.recSoft)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                ChannelLogo(status.logoId, title, 40.dp)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(1.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(c.rec)
                        .border(1.5.dp, c.surface, RoundedCornerShape(999.dp))
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Grabando · $title",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        sub,
                        // Cifras de ancho fijo: el tiempo no "baila" al avanzar
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = c.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val vpn = status.vpnCountry.orEmpty()
                    if (vpn.isNotBlank() && vpn != "null") {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Shield, null, tint = c.text3, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(vpn, style = MaterialTheme.typography.bodySmall, color = c.text2, maxLines = 1)
                    }
                }
            }
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = c.rec, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Detener")
            }
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = c.rec,
            trackColor = Color.Transparent,
            drawStopIndicator = {}
        )
    }
}

/** Aviso fino cuando no hay conexión con el servidor. */
@Composable
fun ConnectionBanner(message: String, onSettings: () -> Unit) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.warnSoft)
            .clickable(onClick = onSettings)
            .padding(start = 16.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Rounded.CloudOff, null, tint = c.warn, modifier = Modifier.size(18.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = c.text, modifier = Modifier.weight(1f))
        TextButton(onClick = onSettings) { Text("Ajustes") }
    }
}
