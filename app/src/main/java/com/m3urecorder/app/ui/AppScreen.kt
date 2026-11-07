package com.m3urecorder.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.m3urecorder.app.service.BackgroundSync
import com.m3urecorder.app.ui.components.ConnectionBanner
import com.m3urecorder.app.ui.components.LiveBar
import com.m3urecorder.app.ui.components.LocalApiClient
import com.m3urecorder.app.ui.components.LocalImageLoader
import com.m3urecorder.app.ui.components.buildImageLoader
import com.m3urecorder.app.ui.screens.ChannelsScreen
import com.m3urecorder.app.ui.screens.RecordSheet
import com.m3urecorder.app.ui.screens.RecordingsScreen
import com.m3urecorder.app.ui.screens.ServerSettingsDialog
import com.m3urecorder.app.ui.theme.AppTheme

/** Pantalla principal: cabecera, avisos, pestañas, panel de grabar, reproductor y ajustes. */
@Composable
fun M3URecorderApp(
    vm: MainViewModel,
    incomingSharedUrl: String?,
    onConsumedSharedUrl: () -> Unit
) {
    val context = LocalContext.current
    val c = AppTheme.colors

    // Iconos de la barra de estado oscuros en tema claro y claros en tema oscuro
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { w ->
                WindowCompat.getInsetsController(w, view).apply {
                    isAppearanceLightStatusBars = !c.isDark
                    isAppearanceLightNavigationBars = !c.isDark
                }
            }
        }
    }

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

    val imageLoader = remember { buildImageLoader(context.applicationContext) }

    CompositionLocalProvider(LocalApiClient provides vm.api, LocalImageLoader provides imageLoader) {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            Scaffold(
                containerColor = c.bg,
                topBar = {
                    Column(Modifier.background(c.surface)) {
                        TopBar(vm)
                        HorizontalDivider(color = c.border)
                        vm.connectionError?.let { ConnectionBanner(it, onSettings = { vm.showSettings = true }) }
                        AnimatedVisibility(vm.status.running) {
                            LiveBar(vm.status, onStop = vm::stopRecording)
                        }
                    }
                },
                bottomBar = { BottomBar(vm) }
            ) { innerPadding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (vm.currentTab) {
                        Tab.CHANNELS -> ChannelsScreen(vm)
                        Tab.RECORDINGS -> RecordingsScreen(vm, onDownload = { item ->
                            vm.downloadUrl(item) { url ->
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                    .onFailure { Toast.makeText(context, "No hay ninguna app para abrir la descarga", Toast.LENGTH_SHORT).show() }
                            }
                        })
                    }
                }
            }

            vm.sheet?.let { sheet ->
                RecordSheet(
                    state = sheet,
                    vpnEnabled = vm.status.vpn.enabled,
                    vpnCountries = vm.vpnCountries,
                    onDismiss = vm::closeSheet,
                    onSubmit = vm::submitSheet,
                    onWatchLive = {
                        sheet.channel?.let(vm::watchChannel)
                        vm.closeSheet()
                    }
                )
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
                    themeMode = vm.themeMode,
                    onThemeChange = vm::setTheme,
                    onDismiss = { vm.showSettings = false },
                    onSaved = vm::onSettingsSaved
                )
            }
        }
    }
}

@Composable
private fun TopBar(vm: MainViewModel) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Marca: el mismo símbolo de "grabar" que la cabecera de la web (anillo y punto rojos)
        RecordMark(color = c.rec, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text("m3uRecorder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.text)
        Spacer(Modifier.weight(1f))
        if (vm.status.vpn.enabled) VpnChip(vm)
        IconButton(onClick = { vm.showSettings = true }) {
            Icon(Icons.Rounded.Settings, "Ajustes", tint = c.text2)
        }
    }
}

/** Símbolo de la app: anillo con un punto en el centro (como el icono y la web). */
@Composable
private fun RecordMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f // mismo dibujo que el SVG de la web (24×24)
        drawCircle(color, radius = 9f * unit, style = Stroke(width = 2f * unit))
        drawCircle(color, radius = 4f * unit)
    }
}

/** País actual de la VPN; al tocarlo se puede cambiar. */
@Composable
private fun VpnChip(vm: MainViewModel) {
    val c = AppTheme.colors
    val vpn = vm.status.vpn
    var open by remember { mutableStateOf(false) }
    val country = vpn.configuredCountry.ifBlank { vpn.ipCountry.ifBlank { "VPN" } }
    val dotColor = when {
        vpn.switching -> c.warn
        vpn.publicIp.isNotBlank() -> c.ok
        else -> c.text3
    }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, c.border, RoundedCornerShape(999.dp))
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(dotColor)
            )
            Icon(Icons.Rounded.Shield, null, tint = c.text2, modifier = Modifier.size(14.dp))
            Text(
                if (vpn.switching) "$country…" else country,
                style = MaterialTheme.typography.labelMedium,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val detail = when {
                vpn.switching -> "Conectando a $country…"
                vpn.publicIp.isNotBlank() -> "Conectada · IP ${vpn.publicIp}"
                vpn.available -> "Gluetun responde, pero aún no hay conexión"
                else -> "No hay conexión con Gluetun"
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = c.text2,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider(color = c.border)
            Text(
                "Cambiar país",
                style = MaterialTheme.typography.labelMedium,
                color = c.text3,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
            )
            val options = vm.vpnCountries.featured.map { it.value to it.label } + vm.vpnCountries.others.map { it to it }
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        vm.changeVpnCountry(value)
                    }
                )
            }
        }
    }
}

@Composable
private fun BottomBar(vm: MainViewModel) {
    val c = AppTheme.colors
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = c.accent,
        selectedTextColor = c.accent,
        indicatorColor = c.accentSoft,
        unselectedIconColor = c.text2,
        unselectedTextColor = c.text2
    )
    Column {
        HorizontalDivider(color = c.border)
        NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
            NavigationBarItem(
                selected = vm.currentTab == Tab.CHANNELS,
                onClick = { vm.currentTab = Tab.CHANNELS },
                icon = { Icon(Icons.Rounded.Tv, null) },
                label = { Text("Canales") },
                colors = colors
            )
            NavigationBarItem(
                selected = vm.currentTab == Tab.RECORDINGS,
                onClick = { vm.currentTab = Tab.RECORDINGS },
                icon = {
                    BadgedBox(badge = {
                        val pending = vm.status.schedulesPending
                        if (pending > 0) Badge(containerColor = c.accent, contentColor = c.onAccent) { Text("$pending") }
                    }) { Icon(Icons.Rounded.VideoLibrary, null) }
                },
                label = { Text("Grabaciones") },
                colors = colors
            )
        }
    }
}
