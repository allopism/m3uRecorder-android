package com.m3urecorder.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3urecorder.app.data.*
import com.m3urecorder.app.ui.components.*
import com.m3urecorder.app.ui.theme.*
import kotlin.math.max

@Composable
fun ChannelsTab(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedGroup: String,
    groups: List<String>,
    onSelectGroup: (String) -> Unit,
    channels: List<ChannelItem>,
    isLoading: Boolean,
    onReloadM3u: () -> Unit,
    onWatchLive: (ChannelItem) -> Unit,
    onSendToDirect: (ChannelItem) -> Unit,
    onSendToSchedule: (ChannelItem) -> Unit
) {
    val context = LocalContext.current
    var showGroupMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Buscador y Selector de Categorías
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("🔍 Buscar canal (ej. La 1, DAZN, Movistar...)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showGroupMenu = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (selectedGroup.isBlank()) "📂 Todas las categorías" else "📂 $selectedGroup",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                        fontSize = 12.5.sp
                    )
                }

                DropdownMenu(
                    expanded = showGroupMenu,
                    onDismissRequest = { showGroupMenu = false },
                    modifier = Modifier
                        .background(BgCard)
                        .heightIn(max = 360.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("📂 Todas las categorías", color = Color.White) },
                        onClick = {
                            showGroupMenu = false
                            onSelectGroup("")
                        }
                    )
                    groups.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g, color = Color.White) },
                            onClick = {
                                showGroupMenu = false
                                onSelectGroup(g)
                            }
                        )
                    }
                }
            }

            FilledTonalButton(onClick = onReloadM3u) {
                Text("🔄 M3U", fontSize = 12.sp)
            }
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentBlue)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            itemsIndexed(
                items = channels,
                key = { idx, ch -> "${idx}_${ch.url}" },
                contentType = { _, _ -> "channel_card" }
            ) { _, ch ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ch.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (ch.group.isNotBlank()) {
                            Text(
                                text = ch.group,
                                color = Color(0xFFC7D2FE),
                                fontSize = 10.5.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FastActionBtn(
                            text = "▶️ Grabar",
                            bgColor = AccentBlue,
                            modifier = Modifier.weight(1f),
                            onClick = { onSendToDirect(ch) }
                        )
                        FastActionBtn(
                            text = "📅 Prog.",
                            bgColor = AccentAmber,
                            modifier = Modifier.weight(1f),
                            onClick = { onSendToSchedule(ch) }
                        )
                        FastActionBtn(
                            text = "👁️ Directo",
                            bgColor = AccentEmerald,
                            modifier = Modifier.weight(1f),
                            onClick = { onWatchLive(ch) }
                        )
                        FastActionBtn(
                            text = "📋",
                            bgColor = Color(0xFF334155),
                            modifier = Modifier.width(42.dp),
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText(ch.name, ch.url))
                                Toast.makeText(context, "📋 Enlace copiado", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}
