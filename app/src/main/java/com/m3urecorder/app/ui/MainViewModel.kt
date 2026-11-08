package com.m3urecorder.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m3urecorder.app.data.ApiClient
import com.m3urecorder.app.data.AppPrefs
import com.m3urecorder.app.data.ChannelItem
import com.m3urecorder.app.data.HistoryItem
import com.m3urecorder.app.data.ScheduleItem
import com.m3urecorder.app.data.ServerStatus
import com.m3urecorder.app.data.VpnCountriesResponse
import com.m3urecorder.app.service.NetworkTransportHelper
import com.m3urecorder.app.service.RecordingNotifier
import com.m3urecorder.app.service.ScheduleAlarms
import com.m3urecorder.app.ui.theme.ThemeMode
import com.m3urecorder.app.util.defaultName
import com.m3urecorder.app.util.epochMillis
import com.m3urecorder.app.util.parseHms
import com.m3urecorder.app.util.stripExt
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.m3urecorder.app.util.toLocalWithFold
import com.m3urecorder.app.util.toOffsetDateTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Pestañas de la barra inferior (las mismas vistas que la web). */
enum class Tab { CHANNELS, RECORDINGS }

/** Qué reproducir: título, archivo del servidor (null si es un canal en directo) y URL. */
data class PlayerTarget(val title: String, val filename: String?, val url: String)

enum class SheetMode { NOW, SCHEDULE }

/** Duraciones rápidas de "Ahora" (valor que entiende el servidor → texto). */
val DURATION_CHOICES = listOf(
    "00:15:00" to "15 min", "00:30:00" to "30 min", "01:00:00" to "1 h",
    "02:00:00" to "2 h", "03:00:00" to "3 h", "custom" to "Otra"
)

/** Formatos de salida con su explicación. */
val FORMAT_CHOICES = listOf(
    "mp4" to "MP4 · el más compatible",
    "mkv" to "MKV · conserva subtítulos y pistas",
    "ts" to "TS · copia exacta del directo",
    "avi" to "AVI"
)

/** Panel para grabar o programar un canal (o un enlace pegado a mano). */
class RecordSheetState(val channel: ChannelItem?, url: String, mode: SheetMode, format: String) {
    var mode by mutableStateOf(mode)
    var url by mutableStateOf(url)
    var durationChoice by mutableStateOf("01:00:00")
    var customDuration by mutableStateOf("")
    // Inicio y fin en hora local. Al cambiarlos se vuelve a la 1.ª vez de una hora repetida.
    private val startState = mutableStateOf(LocalDateTime.now().plusMinutes(5).withSecond(0).withNano(0))
    private val endState = mutableStateOf(LocalDateTime.now().plusMinutes(65).withSecond(0).withNano(0))
    var start: LocalDateTime
        get() = startState.value
        set(v) { startState.value = v; startFold = 0 }
    var end: LocalDateTime
        get() = endState.value
        set(v) { endState.value = v; endFold = 0 }

    /** Cambio de hora de octubre: 0 = 1.ª vez (verano), 1 = 2.ª vez (invierno). */
    var startFold by mutableIntStateOf(0)
    var endFold by mutableIntStateOf(0)

    val startInstant: OffsetDateTime get() = toOffsetDateTime(start, startFold)
    val endInstant: OffsetDateTime get() = toOffsetDateTime(end, endFold)

    /** Segundos reales que dura (correcto aunque haya un cambio de hora en medio). */
    val realSeconds: Long get() = Duration.between(startInstant, endInstant).seconds

    /** Pone el fin a [minutes] minutos REALES del inicio (eligiendo bien la vez si la hora se repite). */
    fun setLengthMinutes(minutes: Long) {
        val (local, fold) = toLocalWithFold(startInstant.plusMinutes(minutes))
        end = local
        endFold = fold
    }
    var name by mutableStateOf(defaultName(channel?.name))
    var format by mutableStateOf(format)
    var vpnCountry by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var submitting by mutableStateOf(false)

    val isManual: Boolean get() = channel == null
    val title: String get() = channel?.name ?: "Grabar un enlace"
}

/**
 * Estado y acciones de la app. Las pantallas solo pintan lo que hay aquí y llaman a sus
 * funciones; así la lógica no se mezcla con la interfaz y sobrevive a giros de pantalla.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Application get() = getApplication()
    val api = ApiClient(app)

    // ── Estado del servidor ──────────────────────
    var status by mutableStateOf(ServerStatus()); private set
    /** null = conectado; si no, el motivo (se muestra bajo la cabecera). */
    var connectionError by mutableStateOf<String?>(null); private set
    /** Cambia al guardar los ajustes: la pantalla reinicia el sondeo con la nueva dirección/token. */
    var settingsVersion by mutableIntStateOf(0); private set
    var vpnCountries by mutableStateOf(VpnCountriesResponse()); private set

    // ── Listas ───────────────────────────────────
    var schedules by mutableStateOf<List<ScheduleItem>>(emptyList()); private set
    var history by mutableStateOf<List<HistoryItem>>(emptyList()); private set
    var channelGroups by mutableStateOf<List<String>>(emptyList()); private set
    var channels by mutableStateOf<List<ChannelItem>>(emptyList()); private set
    var isLoadingChannels by mutableStateOf(false); private set
    /** Ya se intentó cargar una vez: hasta entonces no se muestra "lista vacía". */
    var channelsLoaded by mutableStateOf(false); private set
    var recordingsLoaded by mutableStateOf(false); private set
    var isRefreshing by mutableStateOf(false); private set
    var searchQuery by mutableStateOf(""); private set
    var selectedGroup by mutableStateOf(""); private set
    private var searchJob: Job? = null

    /** Programadas pendientes o en curso, de la más próxima a la más lejana. */
    val upcoming: List<ScheduleItem>
        get() = schedules.filter { it.status == "pending" || it.status == "running" }
            .sortedBy { epochMillis(it.startTime) ?: Long.MAX_VALUE }

    /** Programaciones ya terminadas (completadas, fallidas o canceladas). */
    val pastSchedules: List<ScheduleItem>
        get() = schedules.filter { it.status != "pending" && it.status != "running" }

    // ── Navegación, panel y ajustes ──────────────
    var currentTab by mutableStateOf(Tab.CHANNELS)
    var sheet by mutableStateOf<RecordSheetState?>(null); private set
    var player by mutableStateOf<PlayerTarget?>(null)
    /** Primer arranque: sin dirección del servidor se abren los ajustes directamente. */
    var showSettings by mutableStateOf(!api.isConfigured)
    var themeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(AppPrefs.getThemeMode(app)) }.getOrDefault(ThemeMode.SYSTEM)
    ); private set

    /** Mensajes cortos para mostrar como Toast. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private fun say(text: String) {
        _messages.trySend(text)
    }

    private fun sayError(err: Throwable) = say(err.message ?: "Error desconocido")

    init {
        reloadAll()
    }

    // ── Carga y sondeo ───────────────────────────

    /** Carga inicial (y de nuevo al cambiar dirección o token). */
    fun reloadAll() {
        if (!api.isConfigured) return
        viewModelScope.launch {
            runCatching { vpnCountries = api.fetchVpnCountries() }
            runCatching { channelGroups = api.fetchChannelGroups() }
            searchChannels()
            refreshRecordings()
        }
    }

    /**
     * Pregunta el estado cada pocos segundos. La pantalla la llama solo mientras la app
     * está visible (repeatOnLifecycle): al minimizarla se cancela y no gasta batería.
     */
    suspend fun pollWhileVisible() {
        var wasRunning = status.running
        var lastCounts = status.historyCount to status.schedulesPending
        while (true) {
            if (!api.isConfigured) {
                connectionError = "Configura la dirección del servidor en Ajustes"
            } else if (!NetworkTransportHelper.isServerTransportAvailable(context)) {
                connectionError = "Sin Wi-Fi ni VPN: el servidor no es accesible"
            } else {
                runCatching { api.fetchStatus() }
                    .onSuccess { st ->
                        // Si veníamos de un error (o del primer arranque), las listas pueden estar vacías
                        val reconnected = connectionError != null
                        status = st
                        connectionError = null
                        RecordingNotifier.syncFromStatus(context, st)
                        // Al empezar/terminar una grabación (o cambiar desde la web) se recargan las listas
                        val counts = st.historyCount to st.schedulesPending
                        if (wasRunning != st.running || counts != lastCounts || reconnected) refreshRecordings()
                        if (wasRunning && !st.running) announceResult(st)
                        wasRunning = st.running
                        lastCounts = counts
                    }
                    .onFailure { connectionError = it.message }
            }
            delay(if (status.running) 2000L else 4000L)
        }
    }

    private fun announceResult(st: ServerStatus) {
        val r = st.lastResult ?: return
        when (r.status) {
            "ok" -> say("Grabación terminada")
            "stopped" -> say("Grabación detenida")
            else -> say("La grabación falló: ${r.reason.ifBlank { "motivo desconocido" }}")
        }
    }

    /** Recarga programadas (y sus alarmas locales) y biblioteca. */
    suspend fun refreshRecordings() {
        runCatching { api.fetchSchedules() }.onSuccess {
            schedules = it
            ScheduleAlarms.sync(context, it)
        }
        runCatching { history = api.fetchHistory() }.onSuccess { recordingsLoaded = true }
    }

    /** "Deslizar para actualizar" en las listas. */
    fun pullToRefresh() {
        viewModelScope.launch {
            isRefreshing = true
            if (currentTab == Tab.CHANNELS) {
                runCatching { channelGroups = api.fetchChannelGroups() }
                runCatching { channels = api.searchChannels(searchQuery, selectedGroup) }
            } else {
                refreshRecordings()
            }
            isRefreshing = false
        }
    }

    fun onSettingsSaved() {
        showSettings = false
        connectionError = null
        settingsVersion++
        reloadAll()
    }

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        AppPrefs.setThemeMode(context, mode.name)
    }

    // ── Panel de grabar / programar ──────────────

    fun openSheet(channel: ChannelItem?, mode: SheetMode = SheetMode.NOW, url: String = channel?.url.orEmpty()) {
        sheet = RecordSheetState(channel, url, mode, AppPrefs.getLastFormat(context))
    }

    fun closeSheet() {
        sheet = null
    }

    fun submitSheet() {
        val s = sheet ?: return
        s.error = null
        val url = s.url.trim()
        val name = s.name.trim()
        when {
            url.isEmpty() -> s.error = "Pega el enlace del stream."
            name.isEmpty() -> s.error = "Ponle un nombre a la grabación."
            s.mode == SheetMode.NOW -> submitNow(s, url, name)
            else -> submitSchedule(s, url, name)
        }
    }

    private fun submitNow(s: RecordSheetState, url: String, name: String) {
        val duration = if (s.durationChoice == "custom") s.customDuration.trim() else s.durationChoice
        if (parseHms(duration) == null) {
            s.error = "Escribe la duración como HH:MM:SS (por ejemplo 01:30:00)."
            return
        }
        s.submitting = true
        viewModelScope.launch {
            api.startDirectRecording(url, duration, name, s.format, s.vpnCountry)
                .onSuccess {
                    AppPrefs.setLastFormat(context, s.format)
                    sheet = null
                    say("Grabación iniciada")
                }
                .onFailure { s.error = it.message }
            s.submitting = false
        }
    }

    private fun submitSchedule(s: RecordSheetState, url: String, name: String) {
        when {
            s.realSeconds <= 0 -> { s.error = "La hora de fin tiene que ser posterior a la de inicio."; return }
            !s.endInstant.toInstant().isAfter(Instant.now()) -> { s.error = "Esa franja ya ha pasado."; return }
        }
        // Se envía cada hora con su desfase exacto (+02:00 o +01:00): el servidor no tiene que adivinar
        val startIso = s.startInstant.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val endIso = s.endInstant.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        s.submitting = true
        viewModelScope.launch {
            api.createSchedule(url, startIso, endIso, name, s.format, s.vpnCountry)
                .onSuccess {
                    AppPrefs.setLastFormat(context, s.format)
                    sheet = null
                    say("Grabación programada")
                    refreshRecordings()
                    currentTab = Tab.RECORDINGS
                }
                .onFailure { s.error = it.message }
            s.submitting = false
        }
    }

    /** Enlace recibido desde el menú "Compartir" de otra app: abre el panel con él. */
    fun onSharedUrl(url: String) {
        currentTab = Tab.CHANNELS
        openSheet(null, url = url)
    }

    // ── Grabación en curso y programadas ─────────

    fun stopRecording() {
        viewModelScope.launch {
            api.stopRecording().onSuccess { say("Deteniendo y guardando…") }.onFailure(::sayError)
        }
    }

    fun cancelSchedule(id: String) = scheduleAction { api.cancelSchedule(id) }

    fun deleteSchedule(id: String) = scheduleAction { api.deleteSchedule(id) }

    fun clearFinishedSchedules() = scheduleAction { api.clearFinishedSchedules() }

    private fun scheduleAction(action: suspend () -> Result<String>) {
        viewModelScope.launch {
            action().onFailure(::sayError)
            refreshRecordings()
        }
    }

    // ── Canales ──────────────────────────────────

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        searchChannels(debounceMs = 220L)
    }

    fun onSelectGroup(group: String) {
        selectedGroup = group
        searchChannels()
    }

    /** Busca con un pequeño retardo para no lanzar una petición por cada letra. */
    private fun searchChannels(debounceMs: Long = 0L) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(debounceMs)
            isLoadingChannels = true
            runCatching { channels = api.searchChannels(searchQuery, selectedGroup) }
                .onSuccess { channelsLoaded = true }
            isLoadingChannels = false
        }
    }

    fun reloadM3u() {
        viewModelScope.launch {
            api.reloadM3u()
                .onSuccess { count ->
                    say("Lista recargada: $count canales")
                    runCatching { channelGroups = api.fetchChannelGroups() }
                    searchChannels()
                }
                .onFailure(::sayError)
        }
    }

    // ── VPN ──────────────────────────────────────

    fun changeVpnCountry(country: String) {
        say("Cambiando la VPN a $country…")
        viewModelScope.launch {
            api.changeVpnCountry(country).onSuccess { say(it) }.onFailure(::sayError)
        }
    }

    // ── Biblioteca y reproductor ─────────────────

    fun playRecording(filename: String) {
        player = PlayerTarget(stripExt(filename), filename, api.getDirectStreamUrl(filename))
    }

    fun watchChannel(ch: ChannelItem) {
        player = PlayerTarget(ch.name, null, ch.url)
    }

    fun deleteRecording(item: HistoryItem) {
        viewModelScope.launch {
            api.deleteHistoryItem(item.filename)
                .onSuccess {
                    say("Grabación eliminada")
                    refreshRecordings()
                }
                .onFailure(::sayError)
        }
    }

    /**
     * Enlace de descarga para el navegador. Como el navegador no puede enviar el token,
     * se pide al servidor una firma válida solo para ese archivo.
     */
    fun downloadUrl(item: HistoryItem, onReady: (String) -> Unit) {
        viewModelScope.launch {
            onReady(api.getDownloadUrl(item.filename, api.fileSignature(item.filename)))
        }
    }
}
