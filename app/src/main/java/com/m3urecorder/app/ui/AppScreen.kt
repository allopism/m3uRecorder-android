package com.m3urecorder.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.m3urecorder.app.data.VpnCountryOption
import com.m3urecorder.app.service.BackgroundSync
import com.m3urecorder.app.ui.components.LiveStatusBanner
import com.m3urecorder.app.ui.screens.ChannelsTab
import com.m3urecorder.app.ui.screens.DirectRecordTab
import com.m3urecorder.app.ui.screens.HistoryTab
import com.m3urecorder.app.ui.screens.ScheduleTab
import com.m3urecorder.app.ui.screens.ServerSettingsDialog
import com.m3urecorder.app.ui.theme.*

/** Pantalla principal: cabecera, pestañas, aviso de estado, reproductor y ajustes. */
@Composable
fun M3URecorderApp(
    vm: MainViewModel,
    incomingSharedUrl: String?,
    onConsumedSharedUrl: () -> Unit
) {
    val context = LocalContext.current

    // Permiso de notificaciones (Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) BackgroundSync.configure(context)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Mensajes del ViewModel como Toast
    LaunchedEffect(vm) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Estado en vivo SOLO mientras la app está visible: al minimizarla se cancela
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, vm.settingsVersion) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollWhileVisible() }
    }

    // Enlace compartido desde otra app
    LaunchedEffect(incomingSharedUrl) {
        if (!incomingSharedUrl.isNullOrBlank()) {
            vm.onSharedUrl(incomingSharedUrl)
            onConsumedSharedUrl()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = BgBase,
            topBar = { TopBar(vm) },
            bottomBar = { BottomBar(vm) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val status = vm.status
                // Aviso de estado / progreso (se puede ocultar cuando no hay grabación)
                val isDefaultIdle = !status.running &&
                    (status.progress.isBlank() || status.progress == "Listo para grabar" || status.progress == "Listo")
                val isDismissed = !status.running && status.progress == vm.dismissedBannerText
                if (!isDefaultIdle && !isDismissed) {
                    LiveStatusBanner(
                        status = status,
                        onStopClick = vm::stopRecording,
                        onDismissClick = { vm.dismissedBannerText = status.progress }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (vm.currentTab) {
                        Tab.DIRECT -> {
                            val f = vm.directForm
                            DirectRecordTab(
                                url = f.url,
                                onUrlChange = { f.url = it },
                                duration = f.duration,
                                onDurationChange = { f.duration = it },
                                filename = f.filename,
                                onFilenameChange = { f.filename = it },
                                format = f.format,
                                onFormatChange = { f.format = it },
                                vpnCountry = f.vpnCountry,
                                onVpnCountryChange = { f.vpnCountry = it },
                                vpnCountries = vm.vpnCountries,
                                isRunning = status.running,
                                onStartRecording = vm::startDirectRecording,
                                onStopRecording = vm::stopRecording
                            )
                        }

                        Tab.SCHEDULE -> {
                            val f = vm.scheduleForm
                            ScheduleTab(
                                url = f.url,
                                onUrlChange = { f.url = it },
                                startDt = f.start,
                                onStartDtChange = { f.start = it },
                                endDt = f.end,
                                onEndDtChange = { f.end = it },
                                filename = f.filename,
                                onFilenameChange = { f.filename = it },
                                format = f.format,
                                onFormatChange = { f.format = it },
                                vpnCountry = f.vpnCountry,
                                onVpnCountryChange = { f.vpnCountry = it },
                                vpnCountries = vm.vpnCountries,
                                schedules = vm.schedules,
                                onSaveSchedule = vm::saveSchedule,
                                onRefreshSchedules = { vm.refreshSchedulesAsync() },
                                onClearFinished = vm::clearFinishedSchedules,
                                onCancelJob = vm::cancelSchedule,
                                onDeleteJob = vm::deleteSchedule,
                                onStopActive = vm::stopRecording,
                                onPlayJob = vm::playRecording
                            )
                        }

                        Tab.CHANNELS -> ChannelsTab(
                            searchQuery = vm.searchQuery,
                            onSearchQueryChange = vm::onSearchQueryChange,
                            selectedGroup = vm.selectedGroup,
                            groups = vm.channelGroups,
                            onSelectGroup = vm::onSelectGroup,
                            channels = vm.channels,
                            isLoading = vm.isLoadingChannels,
                            onReloadM3u = vm::reloadM3u,
                            onWatchLive = vm::watchChannel,
                            onSendToDirect = vm::sendChannelToDirect,
                            onSendToSchedule = vm::sendChannelToSchedule
                        )

                        Tab.HISTORY -> HistoryTab(
                            history = vm.history,
                            onRefresh = { vm.refreshHistoryAsync() },
                            onPlayRecording = { vm.playRecording(it.filename) },
                            onDownloadRecording = { item ->
                                vm.downloadUrl(item) { url ->
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        .onFailure { Toast.makeText(context, "No hay app para abrir la descarga", Toast.LENGTH_SHORT).show() }
                                }
                            },
                            onDeleteRecording = vm::deleteRecording
                        )
                    }
                }
            }
        }

        // Reproductor (ventana o pantalla completa sobre todo lo demás)
        vm.player?.let { target ->
            VideoPlayerOverlay(
                title = target.title,
                filenameOrNull = target.filename,
                directStreamUrl = target.url,
                apiClient = vm.api,
                onDismiss = { vm.player = null }
            )
        }

        if (vm.showSettings) {
            ServerSettingsDialog(
                context = context,
                onDismiss = { vm.showSettings = false },
                onSaved = vm::onSettingsSaved
            )
        }
    }
}

@Composable
private fun TopBar(vm: MainViewModel) {
    val status = vm.status
    var showVpnMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSurface)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Fila 1: marca, estado de conexión y ajustes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎬", fontSize = 18.sp)
                }
                Column {
                    Text(
                        text = "M3U8 Stream Recorder",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    val err = vm.connectionError
                    Text(
                        text = if (err == null) "🟢 Conectado · ${vm.api.baseUrl}" else "🔴 $err",
                        color = if (err == null) TextSecondary else Color(0xFFFDA4AF),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = { vm.showSettings = true }) {
                Text("⚙️", fontSize = 18.sp)
            }
        }

        // Fila 2: país de la VPN (toca para cambiarlo) y estado de grabación
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF064E3B).copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.45f)),
                    modifier = Modifier.clickable { showVpnMenu = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (status.vpn.switching) AccentAmber else AccentEmerald)
                        )
                        val vpnTxt = if (status.vpn.switching) "⏳ Cambiando VPN..." else "🛡️ ${status.vpn.activeCountryDisplay} ▾"
                        Text(vpnTxt, color = Color(0xFFA7F3D0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                DropdownMenu(
                    expanded = showVpnMenu,
                    onDismissRequest = { showVpnMenu = false },
                    modifier = Modifier.background(BgCard)
                ) {
                    val list = vm.vpnCountries.featured.ifEmpty {
                        listOf(
                            VpnCountryOption("Spain", "🇪🇸 España (Spain)"),
                            VpnCountryOption("Japan", "🇯🇵 Japón (Japan)"),
                            VpnCountryOption("Switzerland", "🇨🇭 Suiza (Switzerland)"),
                            VpnCountryOption("Netherlands", "🇳🇱 Países Bajos (Netherlands)"),
                            VpnCountryOption("United Kingdom", "🇬🇧 Reino Unido"),
                            VpnCountryOption("United States", "🇺🇸 Estados Unidos")
                        )
                    }
                    list.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label, color = Color.White, fontSize = 13.sp) },
                            onClick = {
                                showVpnMenu = false
                                vm.changeVpnCountry(item.value)
                            }
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (status.running) AccentRose.copy(alpha = 0.2f) else BgInput,
                border = BorderStroke(1.dp, if (status.running) AccentRose else Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (status.running) AccentRose else AccentEmerald)
                    )
                    Text(
                        text = when {
                            !status.running -> "Listo"
                            status.mode == "scheduled" -> "REC · Prog"
                            else -> "REC"
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBar(vm: MainViewModel) {
    NavigationBar(containerColor = BgSurface, tonalElevation = 10.dp) {
        NavigationBarItem(
            selected = vm.currentTab == Tab.DIRECT,
            onClick = { vm.currentTab = Tab.DIRECT },
            icon = { Text("📹", fontSize = 18.sp) },
            label = { Text("Directo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        )
        NavigationBarItem(
            selected = vm.currentTab == Tab.SCHEDULE,
            onClick = {
                vm.currentTab = Tab.SCHEDULE
                vm.refreshSchedulesAsync()
            },
            icon = {
                BadgedBox(badge = {
                    if (vm.status.schedulesPending > 0) {
                        Badge(containerColor = AccentBlue) { Text("${vm.status.schedulesPending}") }
                    }
                }) { Text("📅", fontSize = 18.sp) }
            },
            label = { Text("Programar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        )
        NavigationBarItem(
            selected = vm.currentTab == Tab.CHANNELS,
            onClick = {
                vm.currentTab = Tab.CHANNELS
                vm.loadChannelsIfEmpty()
            },
            icon = { Text("📺", fontSize = 18.sp) },
            label = { Text("Canales", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        )
        NavigationBarItem(
            selected = vm.currentTab == Tab.HISTORY,
            onClick = {
                vm.currentTab = Tab.HISTORY
                vm.refreshHistoryAsync()
            },
            icon = {
                BadgedBox(badge = {
                    if (vm.status.historyCount > 0) {
                        Badge(containerColor = Color(0xFF334155)) { Text("${vm.status.historyCount}", color = Color.White) }
                    }
                }) { Text("📜", fontSize = 18.sp) }
            },
            label = { Text("Historial", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        )
    }
}
