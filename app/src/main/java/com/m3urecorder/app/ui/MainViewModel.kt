package com.m3urecorder.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m3urecorder.app.data.ApiClient
import com.m3urecorder.app.data.ChannelItem
import com.m3urecorder.app.data.HistoryItem
import com.m3urecorder.app.data.ScheduleItem
import com.m3urecorder.app.data.ServerStatus
import com.m3urecorder.app.data.VpnCountriesResponse
import com.m3urecorder.app.service.NetworkTransportHelper
import com.m3urecorder.app.service.RecordingNotifier
import com.m3urecorder.app.service.ScheduleAlarms
import com.m3urecorder.app.util.defaultTimestampFilename
import com.m3urecorder.app.util.sanitizeChannelFilename
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pestañas de la barra inferior. */
enum class Tab { DIRECT, SCHEDULE, CHANNELS, HISTORY }

/** Qué reproducir: título, archivo del servidor (null si es un canal en directo) y URL. */
data class PlayerTarget(val title: String, val filename: String?, val url: String)

/** Formulario de grabación al momento. */
class DirectForm {
    var url by mutableStateOf("")
    var duration by mutableStateOf("01:00:00")
    var filename by mutableStateOf(defaultTimestampFilename("grabacion"))
    var format by mutableStateOf("mkv")
    var vpnCountry by mutableStateOf("")
}

/** Formulario de programación. */
class ScheduleForm {
    var url by mutableStateOf("")
    var start by mutableStateOf(LocalDateTime.now().plusMinutes(5).withSecond(0).withNano(0))
    var end by mutableStateOf(LocalDateTime.now().plusMinutes(65).withSecond(0).withNano(0))
    var filename by mutableStateOf(defaultTimestampFilename("prog"))
    var format by mutableStateOf("mkv")
    var vpnCountry by mutableStateOf("")
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
    /** null = conectado; si no, el motivo (se muestra en la cabecera). */
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
    var searchQuery by mutableStateOf(""); private set
    var selectedGroup by mutableStateOf(""); private set
    private var searchJob: Job? = null

    // ── Formularios y navegación ─────────────────
    val directForm = DirectForm()
    val scheduleForm = ScheduleForm()
    var currentTab by mutableStateOf(Tab.DIRECT)
    var player by mutableStateOf<PlayerTarget?>(null)
    /** Primer arranque: sin dirección del servidor se abren los ajustes directamente. */
    var showSettings by mutableStateOf(!api.isConfigured)
    /** Texto del aviso superior que el usuario ha cerrado (no se vuelve a mostrar). */
    var dismissedBannerText by mutableStateOf("")

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
            runCatching { channels = api.searchChannels("", "") }
            refreshSchedules()
            refreshHistory()
        }
    }

    /**
     * Pregunta el estado cada pocos segundos. La pantalla la llama solo mientras la app
     * está visible (repeatOnLifecycle): al minimizarla se cancela y no gasta batería.
     */
    suspend fun pollWhileVisible() {
        var wasRunning = status.running
        while (true) {
            if (!api.isConfigured) {
                connectionError = "Configura la dirección del servidor en Ajustes"
            } else if (!NetworkTransportHelper.isServerTransportAvailable(context)) {
                connectionError = "Sin Wi-Fi ni VPN: el servidor no es accesible"
            } else {
                runCatching { api.fetchStatus() }
                    .onSuccess { st ->
                        status = st
                        connectionError = null
                        RecordingNotifier.syncFromStatus(context, st)
                        if (st.running) dismissedBannerText = ""
                        // Al empezar o terminar una grabación cambian la cola y la biblioteca
                        if (wasRunning != st.running) {
                            refreshSchedules()
                            refreshHistory()
                        }
                        wasRunning = st.running
                    }
                    .onFailure { connectionError = it.message }
            }
            delay(if (status.running) 2500L else 4000L)
        }
    }

    /** Recarga la cola de programadas y deja las alarmas locales al día. */
    suspend fun refreshSchedules() {
        runCatching { api.fetchSchedules() }.onSuccess {
            schedules = it
            ScheduleAlarms.sync(context, it)
        }
    }

    suspend fun refreshHistory() {
        runCatching { history = api.fetchHistory() }
    }

    fun refreshSchedulesAsync() = viewModelScope.launch { refreshSchedules() }

    fun refreshHistoryAsync() = viewModelScope.launch { refreshHistory() }

    fun onSettingsSaved() {
        showSettings = false
        connectionError = null
        settingsVersion++
        reloadAll()
    }

    // ── Grabación al momento ─────────────────────

    fun startDirectRecording() {
        val f = directForm
        viewModelScope.launch {
            api.startDirectRecording(f.url, f.duration, f.filename, f.format, f.vpnCountry)
                .onSuccess {
                    say("▶️ $it")
                    f.filename = defaultTimestampFilename("grabacion")
                }
                .onFailure(::sayError)
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            api.stopRecording().onSuccess { say("⏹️ $it") }.onFailure(::sayError)
        }
    }

    // ── Programaciones ───────────────────────────

    fun saveSchedule() {
        val f = scheduleForm
        val zone = ZoneId.systemDefault()
        // La hora local del móvil con su desfase (correcto también en los cambios de hora)
        val startIso = f.start.atZone(zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val endIso = f.end.atZone(zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        viewModelScope.launch {
            api.createSchedule(f.url, startIso, endIso, f.filename, f.format, f.vpnCountry)
                .onSuccess { msg ->
                    say("📅 $msg")
                    f.filename = defaultTimestampFilename("prog")
                    refreshSchedules()
                }
                .onFailure(::sayError)
        }
    }

    fun cancelSchedule(id: String) = scheduleAction { api.cancelSchedule(id) }

    fun deleteSchedule(id: String) = scheduleAction { api.deleteSchedule(id) }

    fun clearFinishedSchedules() = scheduleAction { api.clearFinishedSchedules() }

    private fun scheduleAction(action: suspend () -> Result<String>) {
        viewModelScope.launch {
            action().onFailure(::sayError)
            refreshSchedules()
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
            isLoadingChannels = false
        }
    }

    fun loadChannelsIfEmpty() {
        if (channels.isEmpty()) searchChannels()
    }

    fun reloadM3u() {
        viewModelScope.launch {
            api.reloadM3u()
                .onSuccess { count ->
                    say("✅ Lista recargada: $count canales")
                    runCatching { channelGroups = api.fetchChannelGroups() }
                    searchChannels()
                }
                .onFailure(::sayError)
        }
    }

    fun sendChannelToDirect(ch: ChannelItem) {
        directForm.url = ch.url
        directForm.filename = sanitizeChannelFilename(ch.name, prefix = "")
        currentTab = Tab.DIRECT
        say("▶️ '${ch.name}' listo para grabar")
    }

    fun sendChannelToSchedule(ch: ChannelItem) {
        scheduleForm.url = ch.url
        scheduleForm.filename = sanitizeChannelFilename(ch.name, prefix = "prog_")
        currentTab = Tab.SCHEDULE
        say("📅 '${ch.name}' listo para programar")
    }

    /** Enlace recibido desde el menú "Compartir" de otra app. */
    fun onSharedUrl(url: String) {
        directForm.url = url
        scheduleForm.url = url
        currentTab = Tab.DIRECT
        say("🔗 Enlace cargado en Grabar y Programar")
    }

    // ── VPN ──────────────────────────────────────

    fun changeVpnCountry(country: String) {
        say("🌍 Conectando VPN a $country...")
        viewModelScope.launch {
            api.changeVpnCountry(country).onSuccess { say("✅ $it") }.onFailure(::sayError)
        }
    }

    // ── Biblioteca ───────────────────────────────

    fun playRecording(filename: String) {
        player = PlayerTarget(filename, filename, api.getDirectStreamUrl(filename))
    }

    fun watchChannel(ch: ChannelItem) {
        player = PlayerTarget(ch.name, null, ch.url)
    }

    fun deleteRecording(item: HistoryItem) {
        viewModelScope.launch {
            api.deleteHistoryItem(item.filename)
                .onSuccess {
                    say("🗑️ ${item.filename} eliminado")
                    refreshHistory()
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
