package com.m3urecorder.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3urecorder.app.data.ChannelItem
import com.m3urecorder.app.ui.MainViewModel
import com.m3urecorder.app.ui.components.ChannelLogo
import com.m3urecorder.app.ui.components.ChoiceChips
import com.m3urecorder.app.ui.components.EmptyState
import com.m3urecorder.app.ui.components.cardModifier
import com.m3urecorder.app.ui.theme.AppTheme

/** Canales de la lista: buscar, filtrar por categoría y tocar uno para grabarlo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(vm: MainViewModel) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize()) {
        // Buscador + pegar un enlace a mano
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = vm::onSearchQueryChange,
                placeholder = { Text("Buscar canal") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (vm.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { vm.onSearchQueryChange("") }) { Icon(Icons.Rounded.Close, "Borrar búsqueda") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = c.surface,
                    focusedContainerColor = c.surface,
                    unfocusedBorderColor = c.border
                ),
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = { vm.openSheet(null) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.height(56.dp)
            ) {
                Icon(Icons.Rounded.Link, null, modifier = Modifier.padding(end = 6.dp))
                Text("Enlace")
            }
        }

        if (vm.channelGroups.isNotEmpty()) {
            ChoiceChips(
                options = listOf("" to "Todos") + vm.channelGroups.map { it to it },
                selected = vm.selectedGroup,
                onSelect = vm::onSelectGroup,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .height(3.dp)
                .fillMaxWidth()
        ) {
            if (vm.isLoadingChannels) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent)
        }

        PullToRefreshBox(
            isRefreshing = vm.isRefreshing,
            onRefresh = vm::pullToRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (vm.channels.isEmpty() && vm.channelsLoaded && !vm.isLoadingChannels) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (vm.searchQuery.isNotBlank() || vm.selectedGroup.isNotBlank()) {
                            EmptyState(
                                Icons.Rounded.SearchOff,
                                "No hay canales que coincidan",
                                "Prueba con otro nombre o con otra categoría."
                            )
                        } else {
                            EmptyState(
                                Icons.Rounded.Tv,
                                "La lista de canales está vacía",
                                "Revisa el archivo lista.m3u del servidor y recárgalo.",
                                action = {
                                    TextButton(onClick = vm::reloadM3u) {
                                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.padding(end = 6.dp))
                                        Text("Recargar lista")
                                    }
                                }
                            )
                        }
                    }
                }
                items(vm.channels, key = { it.url + it.name }) { ch ->
                    ChannelTile(ch, onClick = { vm.openSheet(ch) }, onLongClick = { vm.watchChannel(ch) })
                }
            }
        }
    }
}

/** Logo grande y nombre. Tocar: grabar o programar. Mantener pulsado: verlo en directo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelTile(ch: ChannelItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = AppTheme.colors
    Column(
        modifier = cardModifier()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Ver en directo")
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ChannelLogo(ch.logoId, ch.name, size = 64.dp, corner = 12.dp)
        Text(
            ch.name,
            style = MaterialTheme.typography.labelMedium,
            color = c.text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (ch.group.isNotBlank()) {
            Text(
                ch.group,
                style = MaterialTheme.typography.labelSmall,
                color = c.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
