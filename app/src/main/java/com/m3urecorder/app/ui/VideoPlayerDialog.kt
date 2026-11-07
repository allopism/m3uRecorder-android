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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.ui.draw.clip
import com.m3urecorder.app.ui.theme.AppTheme
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
            Toast.makeText(context, "Audio: ${audioTracks.getOrNull(index)?.label ?: "Pista ${index + 1}"}", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Audio: ${audioTracks.getOrNull(index)?.label ?: "Pista ${index + 1}"}", Toast.LENGTH_SHORT).show()
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
                        .background(Color.Black.copy(alpha = 0.6f))
                        .displayCutoutPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    AudioTrackButton(audioTracks, activeTrackIdx, showAudioMenu, { showAudioMenu = it }, ::selectAudioTrack, onDark = true)
                    IconButton(onClick = { toggleFullscreen(false) }) {
                        Icon(Icons.Rounded.FullscreenExit, "Salir de pantalla completa", tint = Color.White)
                    }
                }
            }
        }
    } else {
        // ── Ventana sobre la app (vertical) ──
        val c = AppTheme.colors
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                color = c.surface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Cabecera: título, códec y cerrar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Título + códec ocupan todo el espacio libre; así la X queda siempre a la derecha
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = title,
                                color = c.text,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (videoCodec.isNotBlank()) {
                                Text(
                                    text = videoCodec,
                                    color = c.text2,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(c.surface2)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Cerrar", tint = c.text2) }
                    }

                    // Vídeo 16:9
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

                    // Acciones: pista de audio, pantalla completa, abrir fuera y copiar enlace
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AudioTrackButton(audioTracks, activeTrackIdx, showAudioMenu, { showAudioMenu = it }, ::selectAudioTrack, onDark = false)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(externalUrl), "video/*")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(intent, "Abrir con…"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "No hay ningún reproductor externo", Toast.LENGTH_SHORT).show()
                            }
                        }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Abrir con otra app (VLC…)", tint = c.text2) }
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Stream URL", externalUrl))
                            Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Rounded.ContentCopy, "Copiar enlace", tint = c.text2) }
                        IconButton(onClick = { toggleFullscreen(true) }) {
                            Icon(Icons.Rounded.Fullscreen, "Pantalla completa", tint = c.text2)
                        }
                    }
                }
            }
        }
    }
}

/** Botón de pista de audio con su menú (solo útil si la grabación trae varias). */
@Composable
private fun AudioTrackButton(
    tracks: List<AudioTrackInfo>,
    activeIdx: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
    onDark: Boolean
) {
    val c = AppTheme.colors
    val fg = if (onDark) Color.White else c.text2
    Box {
        TextButton(onClick = { onExpandedChange(true) }) {
            Icon(Icons.Rounded.Headphones, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (tracks.size > 1) "Pista ${activeIdx + 1} de ${tracks.size}" else "Audio",
                color = fg,
                style = MaterialTheme.typography.labelMedium
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            if (tracks.isEmpty()) {
                DropdownMenuItem(text = { Text("Pista 1 · Principal") }, onClick = { onExpandedChange(false) })
            }
            tracks.forEachIndexed { idx, track ->
                DropdownMenuItem(
                    text = {
                        Text(
                            track.label,
                            color = if (idx == activeIdx) c.accent else c.text,
                            fontWeight = if (idx == activeIdx) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        if (idx == activeIdx) Icon(Icons.Rounded.Check, null, tint = c.accent)
                        else Spacer(Modifier.size(24.dp))
                    },
                    onClick = { onSelect(idx) }
                )
            }
        }
    }
}
