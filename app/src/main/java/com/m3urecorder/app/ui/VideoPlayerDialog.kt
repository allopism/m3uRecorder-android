package com.m3urecorder.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.m3urecorder.app.data.ApiClient
import com.m3urecorder.app.data.AudioTrackInfo

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerOverlay(
    title: String,
    filenameOrNull: String?,
    directStreamUrl: String,
    apiClient: ApiClient,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var audioTracks by remember { mutableStateOf<List<AudioTrackInfo>>(emptyList()) }
    var activeTrackIdx by remember { mutableIntStateOf(0) }
    var videoCodec by remember { mutableStateOf("") }
    var showAudioMenu by remember { mutableStateOf(false) }
    var usePipeFallback by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    // Firma del archivo para abrirlo fuera de la app (VLC no puede enviar el token)
    var fileSig by remember { mutableStateOf("") }
    val externalUrl = if (filenameOrNull.isNullOrBlank()) directStreamUrl
        else apiClient.getDirectStreamUrl(filenameOrNull, fileSig)

    val exoPlayer = remember {
        // El token solo viaja a nuestro servidor; los directos de canales van sin cabeceras
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(apiClient.authHeadersFor(directStreamUrl))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory))
            .build().apply {
            setMediaItem(MediaItem.fromUri(directStreamUrl))
            prepare()
            playWhenReady = true
        }
    }

    fun applyOrientationAndSystemBars(fullscreen: Boolean) {
        val act = activity ?: return
        val window = act.window ?: return
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        if (fullscreen) {
            // Permitir que la ventana principal cubra también la zona de la cámara (Notch/Cutout) al girar a izquierda o derecha
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val params = window.attributes
                params.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                window.attributes = params
            }
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val params = window.attributes
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                window.attributes = params
            }
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun toggleFullscreen(target: Boolean) {
        isFullscreen = target
        applyOrientationAndSystemBars(target)
    }

    LaunchedEffect(filenameOrNull) {
        if (!filenameOrNull.isNullOrBlank()) {
            runCatching {
                val info = apiClient.fetchMediaInfo(filenameOrNull)
                videoCodec = info.videoCodec.uppercase()
                audioTracks = info.audioTracks
                fileSig = info.sig
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            applyOrientationAndSystemBars(false)
            exoPlayer.release()
        }
    }

    fun selectAudioTrack(index: Int) {
        activeTrackIdx = index
        showAudioMenu = false

        val currentTracks = exoPlayer.currentTracks
        val audioGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (!usePipeFallback && audioGroups.size > index) {
            val targetGroup = audioGroups[index].mediaTrackGroup
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(targetGroup, 0))
                .build()
            Toast.makeText(context, "🎧 ${audioTracks.getOrNull(index)?.label ?: "Pista ${index + 1}"}", Toast.LENGTH_SHORT).show()
            return
        }

        if (!filenameOrNull.isNullOrBlank()) {
            val currentSec = (exoPlayer.currentPosition / 1000.0).coerceAtLeast(0.0)
            val newUrl = if (index == 0) {
                apiClient.getDirectStreamUrl(filenameOrNull)
            } else {
                usePipeFallback = true
                apiClient.getPipeTrackStreamUrl(filenameOrNull, index, currentSec)
            }
            exoPlayer.setMediaItem(MediaItem.fromUri(newUrl))
            exoPlayer.prepare()
            if (index == 0 && currentSec > 0) {
                exoPlayer.seekTo((currentSec * 1000).toLong())
            }
            exoPlayer.playWhenReady = true
            Toast.makeText(context, "🎧 ${audioTracks.getOrNull(index)?.label ?: "Pista ${index + 1}"}", Toast.LENGTH_SHORT).show()
        }
    }

    if (isFullscreen) {
        // ── MODO PANTALLA COMPLETA DIRECTO EN EL LIENZO PRINCIPAL (SIN DIALOG QUE RECORTE EL CUTOUT) ──
        BackHandler(enabled = true) {
            toggleFullscreen(false)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(100f)
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowFastForwardButton(true)
                        setShowRewindButton(true)
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = (visibility == View.VISIBLE)
                        })
                        setFullscreenButtonClickListener {
                            toggleFullscreen(false)
                        }
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Barra superior flotante en pantalla completa
            if (controlsVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .displayCutoutPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🎬", fontSize = 16.sp)
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box {
                            OutlinedButton(
                                onClick = { showAudioMenu = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                val label = if (audioTracks.size > 1) {
                                    "🎧 Pista ${activeTrackIdx + 1}/${audioTracks.size}"
                                } else {
                                    "🎧 Audio"
                                }
                                Text(label, color = Color(0xFF93C5FD), fontSize = 12.sp)
                            }

                            DropdownMenu(
                                expanded = showAudioMenu,
                                onDismissRequest = { showAudioMenu = false },
                                modifier = Modifier.background(Color(0xFF1E293B))
                            ) {
                                audioTracks.forEachIndexed { idx, track ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = (if (idx == activeTrackIdx) "✓ " else "   ") + track.label,
                                                color = if (idx == activeTrackIdx) Color(0xFF60A5FA) else Color.White,
                                                fontWeight = if (idx == activeTrackIdx) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = { selectAudioTrack(idx) }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { toggleFullscreen(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("↙ Salir pantalla completa", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    } else {
        // ── MODO VENTANA MODAL (VERTICAL) ──
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F172A),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Cabecera del Reproductor
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111827))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🎬", fontSize = 16.sp)
                            Text(
                                text = title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (videoCodec.isNotBlank()) {
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = videoCodec,
                                        color = Color(0xFF93C5FD),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        TextButton(onClick = onDismiss) {
                            Text("✕ Cerrar", color = Color(0xFFFDA4AF), fontWeight = FontWeight.Bold)
                        }
                    }

                    // Vista de Vídeo ExoPlayer (16:9)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = true
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShowFastForwardButton(true)
                                    setShowRewindButton(true)
                                    setFullscreenButtonClickListener {
                                        toggleFullscreen(true)
                                    }
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        Gravity.CENTER
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Barra de acciones Pro (Audio + Pantalla Completa + VLC + Copiar URL)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111827))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1.1f)) {
                                Button(
                                    onClick = { showAudioMenu = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    val label = if (audioTracks.size > 1) {
                                        "🎧 Pista ${activeTrackIdx + 1}/${audioTracks.size}"
                                    } else {
                                        "🎧 Audio (${audioTracks.size.coerceAtLeast(1)})"
                                    }
                                    Text(label, color = Color(0xFF93C5FD), fontSize = 11.5.sp, maxLines = 1)
                                }

                                DropdownMenu(
                                    expanded = showAudioMenu,
                                    onDismissRequest = { showAudioMenu = false },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                ) {
                                    if (audioTracks.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Pista 1 · Principal", color = Color.White) },
                                            onClick = { showAudioMenu = false }
                                        )
                                    } else {
                                        audioTracks.forEachIndexed { idx, track ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = (if (idx == activeTrackIdx) "✓ " else "   ") + track.label,
                                                        color = if (idx == activeTrackIdx) Color(0xFF60A5FA) else Color.White,
                                                        fontWeight = if (idx == activeTrackIdx) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                onClick = { selectAudioTrack(idx) }
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { toggleFullscreen(true) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text("⛶ Completa", color = Color.White, fontSize = 11.5.sp, maxLines = 1)
                            }

                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(externalUrl), "video/*")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Abrir stream en..."))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No se encontró reproductor externo", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(0.95f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text("🟠 VLC", color = Color.White, fontSize = 11.5.sp, maxLines = 1)
                            }

                            Button(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Stream URL", externalUrl))
                                    Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text("🔗", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
