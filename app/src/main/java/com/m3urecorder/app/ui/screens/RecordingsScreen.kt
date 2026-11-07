package com.m3urecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3urecorder.app.data.HistoryItem
import com.m3urecorder.app.data.ScheduleItem
import com.m3urecorder.app.ui.MainViewModel
import com.m3urecorder.app.ui.Tab
import com.m3urecorder.app.ui.components.ChannelLogo
import com.m3urecorder.app.ui.components.EmptyState
import com.m3urecorder.app.ui.components.IconTile
import com.m3urecorder.app.ui.components.LocalApiClient
import com.m3urecorder.app.ui.components.SectionHeader
import com.m3urecorder.app.ui.components.ServerImage
import com.m3urecorder.app.ui.components.Tag
import com.m3urecorder.app.ui.components.TagStyle
import com.m3urecorder.app.ui.components.cardModifier
import com.m3urecorder.app.ui.components.rememberNow
import com.m3urecorder.app.ui.theme.AppTheme
import com.m3urecorder.app.util.countdownText
import com.m3urecorder.app.util.fmtDay
import com.m3urecorder.app.util.fmtTime
import com.m3urecorder.app.util.formatBytes
import com.m3urecorder.app.util.parseIso
import com.m3urecorder.app.util.shortDuration
import com.m3urecorder.app.util.stripExt

/** Grabaciones: próximas, biblioteca y programaciones anteriores. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(vm: MainViewModel, onDownload: (HistoryItem) -> Unit) {
    var toDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var pastOpen by rememberSaveable { mutableStateOf(false) }
    val upcoming = vm.upcoming
    val past = vm.pastSchedules
    val library = vm.history

    PullToRefreshBox(isRefreshing = vm.isRefreshing, onRefresh = vm::pullToRefresh, modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (upcoming.isNotEmpty()) {
                fullRow { SectionHeader("Próximas", subtitle = "${upcoming.size}") }
                items(upcoming, key = { "up_" + it.id }, span = { GridItemSpan(maxLineSpan) }) { job ->
                    UpcomingRow(job, onCancel = { vm.cancelSchedule(job.id) }, onStop = vm::stopRecording)
                }
            }

            val total = library.sumOf { it.filesizeBytes }
            fullRow {
                SectionHeader(
                    "Biblioteca",
                    subtitle = if (library.isEmpty()) "" else
                        "${library.size} ${if (library.size == 1) "grabación" else "grabaciones"} · ${formatBytes(total)}"
                )
            }
            if (library.isEmpty()) {
                fullRow {
                    EmptyState(
                        Icons.Rounded.Movie,
                        "Todavía no hay grabaciones",
                        "Elige un canal para grabarlo ahora o programarlo.",
                        action = {
                            FilledTonalButton(onClick = { vm.currentTab = Tab.CHANNELS }) {
                                Icon(Icons.Rounded.Tv, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ver canales")
                            }
                        }
                    )
                }
            }
            items(library, key = { "lib_" + it.filename + it.start }) { item ->
                LibraryCard(
                    item,
                    onPlay = { vm.playRecording(item.filename) },
                    onDownload = { onDownload(item) },
                    onDelete = { toDelete = item.filename }
                )
            }

            if (past.isNotEmpty()) {
                fullRow {
                    SectionHeader(
                        "Programaciones anteriores",
                        subtitle = "${past.size}",
                        modifier = Modifier.clickable { pastOpen = !pastOpen },
                        action = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (pastOpen) TextButton(onClick = vm::clearFinishedSchedules) { Text("Limpiar") }
                                Icon(
                                    Icons.Rounded.ExpandMore, if (pastOpen) "Plegar" else "Desplegar",
                                    tint = AppTheme.colors.text2,
                                    modifier = Modifier.rotate(if (pastOpen) 180f else 0f)
                                )
                            }
                        }
                    )
                }
                if (pastOpen) {
                    items(past, key = { "past_" + it.id }, span = { GridItemSpan(maxLineSpan) }) { job ->
                        PastRow(job, onDelete = { vm.deleteSchedule(job.id) })
                    }
                }
            }
        }
    }

    toDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Eliminar la grabación?") },
            text = { Text("Se borrará «${stripExt(file)}» del servidor. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.history.firstOrNull { it.filename == file }?.let(vm::deleteRecording)
                    toDelete = null
                }) { Text("Eliminar", color = AppTheme.colors.rec) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}

private fun LazyGridScope.fullRow(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun jobTitle(job: ScheduleItem) = job.channel.ifBlank { stripExt(job.filename) }

@Composable
private fun UpcomingRow(job: ScheduleItem, onCancel: () -> Unit, onStop: () -> Unit) {
    val c = AppTheme.colors
    val now = rememberNow()
    val start = parseIso(job.startTime)
    val end = parseIso(job.endTime)
    val running = job.status == "running"
    Row(
        modifier = cardModifier().fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChannelLogo(job.logoId, jobTitle(job), 44.dp)
        Column(Modifier.weight(1f)) {
            Text(jobTitle(job), style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val whenText = if (start != null && end != null) "${fmtDay(start)} · ${fmtTime(start)}–${fmtTime(end)}" else ""
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(whenText, job.format.uppercase()).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = c.text2, maxLines = 1
                )
                if (job.vpnCountry.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Shield, null, tint = c.text3, modifier = Modifier.size(12.dp))
                    Text(" ${job.vpnCountry}", style = MaterialTheme.typography.bodySmall, color = c.text2, maxLines = 1)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (running) {
                Tag("Grabando", TagStyle.REC)
                OutlinedButton(onClick = onStop, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(16.dp))
                    Text(" Detener")
                }
            } else {
                Text(
                    start?.let { countdownText(it.toInstant().toEpochMilli(), now) } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.accent
                )
                TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Cancelar", color = c.text2)
                }
            }
        }
    }
}

/** Tarjeta de la biblioteca: miniatura con el logo del canal y la duración, título y datos. */
@Composable
private fun LibraryCard(item: HistoryItem, onPlay: () -> Unit, onDownload: () -> Unit, onDelete: () -> Unit) {
    val c = AppTheme.colors
    val api = LocalApiClient.current
    val hasFile = item.filesizeBytes > 0
    val title = stripExt(item.filename)
    val whenDt = parseIso(item.start)
    val meta = listOfNotNull(
        whenDt?.let { "${fmtDay(it)}, ${fmtTime(it)}" },
        if (hasFile) formatBytes(item.filesizeBytes) else null,
        item.format.uppercase().ifBlank { null },
        item.vpnCountry.ifBlank { null }
    ).joinToString(" · ")

    Column(cardModifier()) {
        // Miniatura 16:9
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(c.surface2)
                .then(if (hasFile) Modifier.clickable(onClick = onPlay) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            val fallback = @Composable {
                if (item.channel.isNotBlank()) ChannelLogo(item.logoId, item.channel, 48.dp)
                else IconTile(if (hasFile) Icons.Rounded.Movie else Icons.Rounded.ErrorOutline, 48.dp)
            }
            if (hasFile && api != null) {
                ServerImage(api.thumbUrl(item.filename), Modifier.fillMaxSize(), fallback = fallback)
            } else {
                fallback()
            }
            if (hasFile) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.PlayArrow, "Ver", tint = Color.White) }
            }
            if (hasFile && item.logoId.isNotBlank()) {
                ChannelLogo(
                    item.logoId, item.channel.ifBlank { title }, 30.dp, corner = 8.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }
            val dur = item.mediaDurationSeconds
            if (hasFile && dur > 0) {
                Text(
                    shortDuration(dur),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Row(
            Modifier.padding(start = 12.dp, top = 10.dp, bottom = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(meta, style = MaterialTheme.typography.bodySmall, color = c.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                when (item.status) {
                    "failed" -> {
                        Spacer(Modifier.size(2.dp))
                        Tag(if (hasFile) "Incompleta" else "Fallida", if (hasFile) TagStyle.WARN else TagStyle.REC)
                        if (item.statusReason.isNotBlank()) {
                            Text(item.statusReason, style = MaterialTheme.typography.labelSmall, color = c.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    "stopped" -> {
                        Spacer(Modifier.size(2.dp))
                        Tag("Detenida antes de tiempo")
                    }
                }
            }
            Column {
                if (hasFile) {
                    IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Download, "Descargar", tint = c.text2, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Delete, "Eliminar", tint = c.text2, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun PastRow(job: ScheduleItem, onDelete: () -> Unit) {
    val c = AppTheme.colors
    val start = parseIso(job.startTime)
    val end = parseIso(job.endTime)
    val (label, style) = when (job.status) {
        "ok" -> "Completada" to TagStyle.OK
        "stopped" -> "Detenida" to TagStyle.NEUTRAL
        "failed" -> "Fallida" to TagStyle.REC
        "cancelled" -> "Cancelada" to TagStyle.NEUTRAL
        else -> job.status to TagStyle.NEUTRAL
    }
    Row(
        modifier = cardModifier().fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChannelLogo(job.logoId, jobTitle(job), 36.dp)
        Column(Modifier.weight(1f)) {
            Text(jobTitle(job), style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val whenText = if (start != null && end != null) "${fmtDay(start)} · ${fmtTime(start)}–${fmtTime(end)}" else ""
            Text(whenText, style = MaterialTheme.typography.bodySmall, color = c.text2, maxLines = 1)
            if (job.status == "failed" && job.statusReason.isNotBlank()) {
                Text(job.statusReason, style = MaterialTheme.typography.labelSmall, color = c.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Tag(label, style)
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Close, "Quitar de la lista", tint = c.text3) }
    }
}
