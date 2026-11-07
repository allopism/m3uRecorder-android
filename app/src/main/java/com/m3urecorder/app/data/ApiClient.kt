package com.m3urecorder.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Error de la API con un mensaje listo para mostrar al usuario. */
class ApiException(val code: Int, message: String) : Exception(message) {
    val isUnauthorized: Boolean get() = code == 401
}

class ApiClient(
    private val context: Context,
    private val overrideUrl: String? = null,
    private val overrideToken: String? = null
) {

    val baseUrl: String
        get() = overrideUrl?.let { AppPrefs.normalizeUrl(it) } ?: AppPrefs.getServerUrl(context)

    private val token: String
        get() = overrideToken?.trim() ?: AppPrefs.getToken(context)

    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    /**
     * Cabeceras para pedir archivos al servidor desde el reproductor.
     * Solo se añaden para direcciones de nuestro servidor: el token nunca se envía a terceros.
     */
    fun authHeadersFor(url: String): Map<String, String> =
        if (token.isNotBlank() && isConfigured && url.startsWith(baseUrl)) mapOf("Authorization" to "Bearer $token")
        else emptyMap()

    private fun request(
        path: String,
        method: String = "GET",
        formParams: Map<String, String>? = null,
        timeoutMs: Int = 8000
    ): String {
        if (!isConfigured) throw ApiException(0, "Configura la dirección del servidor en Ajustes")
        val conn = try {
            URL("$baseUrl$path").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw ApiException(0, "Dirección del servidor no válida")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Accept", "application/json")
            if (token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $token")

            if (formParams != null && method == "POST") {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                val body = formParams.entries.joinToString("&") { (k, v) ->
                    "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val serverMsg = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
                val msg = when {
                    code == 401 -> "Token incorrecto o ausente (revisa Ajustes)"
                    serverMsg.isNotBlank() -> serverMsg
                    else -> "El servidor respondió con un error ($code)"
                }
                throw ApiException(code, msg)
            }
            return text
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException(0, "No se puede conectar con el servidor")
        } finally {
            conn.disconnect()
        }
    }

    private fun requestObject(path: String, method: String = "GET", form: Map<String, String>? = null, timeoutMs: Int = 8000): JSONObject {
        val raw = request(path, method, form, timeoutMs)
        return try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw ApiException(0, "Respuesta inesperada: ¿es la dirección de m3uRecorder?")
        }
    }

    private fun requestArray(path: String): JSONArray {
        val raw = request(path)
        return try {
            JSONArray(raw)
        } catch (e: JSONException) {
            throw ApiException(0, "Respuesta inesperada: ¿es la dirección de m3uRecorder?")
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key)

    // ── Estado y VPN ─────────────────────────────

    suspend fun fetchStatus(timeoutMs: Int = 5000): ServerStatus = withContext(Dispatchers.IO) {
        val obj = requestObject("/status", timeoutMs = timeoutMs)
        val vpnObj = obj.optJSONObject("vpn") ?: JSONObject()
        val lrObj = obj.optJSONObject("last_result")

        ServerStatus(
            running = obj.optBoolean("running", false),
            progress = obj.optString("progress", "Listo"),
            startTime = obj.optNullableString("start_time"),
            durationRequested = if (obj.isNull("duration_requested")) null else obj.optInt("duration_requested"),
            filename = obj.optNullableString("filename"),
            channel = obj.optString("channel", ""),
            mode = obj.optNullableString("mode"),
            vpnCountry = obj.optNullableString("vpn_country"),
            serverTime = obj.optString("server_time", ""),
            schedulesPending = obj.optInt("schedules_pending", 0),
            historyCount = obj.optInt("history_count", 0),
            vpn = VpnInfo(
                enabled = vpnObj.optBoolean("enabled", true),
                available = vpnObj.optBoolean("available", false),
                status = vpnObj.optString("status", "unknown"),
                publicIp = vpnObj.optString("public_ip", ""),
                ipCountry = vpnObj.optString("ip_country", ""),
                configuredCountry = vpnObj.optString("configured_country", ""),
                switching = vpnObj.optBoolean("switching", false)
            ),
            lastResult = lrObj?.let { LastResultInfo(it.optString("status", ""), it.optString("reason", "")) }
        )
    }

    /** Comprueba dirección y token. Devuelve un texto corto para mostrar en Ajustes. */
    suspend fun testConnection(): Result<String> = runCatching {
        val st = fetchStatus()
        if (st.running) "Conectado · grabando ahora" else "Conectado correctamente"
    }

    suspend fun fetchVpnCountries(): VpnCountriesResponse = withContext(Dispatchers.IO) {
        val obj = requestObject("/api/vpn/countries")
        val featArr = obj.optJSONArray("featured") ?: JSONArray()
        val othersArr = obj.optJSONArray("others") ?: JSONArray()
        val featured = List(featArr.length()) { i ->
            val item = featArr.getJSONObject(i)
            VpnCountryOption(value = item.optString("value"), label = item.optString("label"))
        }
        val others = List(othersArr.length()) { i -> othersArr.getString(i) }
        VpnCountriesResponse(featured = featured, others = others)
    }

    suspend fun changeVpnCountry(country: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val obj = requestObject("/api/vpn/country", "POST", mapOf("country" to country), timeoutMs = 32000)
            obj.optString("message", "Conectado a $country")
        }
    }

    // ── Grabación ────────────────────────────────

    suspend fun startDirectRecording(
        url: String,
        duration: String,
        filename: String,
        format: String,
        vpnCountry: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val obj = requestObject(
                "/record", "POST",
                mapOf("url" to url, "duration" to duration, "filename" to filename, "format" to format, "vpn_country" to vpnCountry)
            )
            obj.optString("message", "Grabación iniciada")
        }
    }

    suspend fun stopRecording(): Result<String> = withContext(Dispatchers.IO) {
        runCatching { requestObject("/stop", "POST").optString("message", "Deteniendo grabación...") }
    }

    // ── Programaciones ───────────────────────────

    suspend fun createSchedule(
        url: String,
        startIso: String,
        endIso: String,
        filename: String,
        format: String,
        vpnCountry: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val obj = requestObject(
                "/schedule", "POST",
                mapOf(
                    "url" to url, "start" to startIso, "end" to endIso,
                    "filename" to filename, "format" to format, "vpn_country" to vpnCountry
                )
            )
            obj.optString("message", "Grabación programada")
        }
    }

    suspend fun fetchSchedules(): List<ScheduleItem> = withContext(Dispatchers.IO) {
        val arr = requestArray("/schedules")
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ScheduleItem(
                id = o.optString("id"),
                url = o.optString("url"),
                filename = o.optString("filename"),
                channel = o.optString("channel", ""),
                format = o.optString("format", "mp4"),
                startTime = o.optString("start_time"),
                endTime = o.optString("end_time"),
                durationSeconds = o.optInt("duration_seconds", 0),
                vpnCountry = o.optString("vpn_country", ""),
                status = o.optString("status", "pending"),
                statusReason = o.optString("status_reason", ""),
                filesizeHuman = o.optString("filesize_human", "")
            )
        }.reversed()
    }

    suspend fun cancelSchedule(id: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { requestObject("/schedules/cancel/${enc(id)}", "POST").optString("message", "Cancelada") }
    }

    suspend fun deleteSchedule(id: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { requestObject("/schedules/delete/${enc(id)}", "POST").optString("message", "Eliminada") }
    }

    suspend fun clearFinishedSchedules(): Result<String> = withContext(Dispatchers.IO) {
        runCatching { requestObject("/schedules/clear", "POST").optString("message", "Limpiadas") }
    }

    // ── Canales ──────────────────────────────────

    suspend fun fetchChannelGroups(): List<String> = withContext(Dispatchers.IO) {
        val arr = requestArray("/api/channels/groups")
        List(arr.length()) { idx -> arr.getString(idx) }
    }

    suspend fun searchChannels(query: String, group: String): List<ChannelItem> = withContext(Dispatchers.IO) {
        val arr = requestArray("/api/channels?q=${enc(query)}&group=${enc(group)}")
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ChannelItem(
                name = o.optString("name"),
                url = o.optString("url"),
                group = o.optString("group", ""),
                logoId = o.optString("logo_id", "")
            )
        }
    }

    suspend fun reloadM3u(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching { requestObject("/api/channels/reload", "POST").optInt("count", 0) }
    }

    // ── Biblioteca ───────────────────────────────

    suspend fun fetchHistory(): List<HistoryItem> = withContext(Dispatchers.IO) {
        val arr = requestArray("/history")
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            HistoryItem(
                filename = o.optString("filename"),
                url = o.optString("url"),
                channel = o.optString("channel", ""),
                start = o.optString("start"),
                end = o.optString("end"),
                durationRequested = o.optString("duration_requested", ""),
                durationSeconds = o.optInt("duration_seconds", 0),
                mediaDurationSeconds = o.optInt("media_duration_seconds", o.optInt("duration_seconds", 0)),
                format = o.optString("format", "mp4"),
                filesizeBytes = o.optLong("filesize_bytes", 0L),
                filesizeHuman = o.optString("filesize_human", "0 B"),
                status = o.optString("status", ""),
                statusReason = o.optString("status_reason", ""),
                vpnCountry = o.optString("vpn_country", ""),
                type = o.optString("type", "immediate")
            )
        }.reversed()
    }

    suspend fun deleteHistoryItem(filename: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { requestObject("/history/delete/${enc(filename)}", "POST").optString("message", "Grabación eliminada") }
    }

    suspend fun fetchMediaInfo(filename: String): MediaInfoResponse = withContext(Dispatchers.IO) {
        val obj = requestObject("/api/media-info/${enc(filename)}", timeoutMs = 15000)
        val tracksArr = obj.optJSONArray("audio_tracks") ?: JSONArray()
        val tracks = List(tracksArr.length()) { i ->
            val t = tracksArr.getJSONObject(i)
            AudioTrackInfo(
                index = t.optInt("index", i),
                codec = t.optString("codec", ""),
                lang = t.optString("lang", ""),
                label = t.optString("label", "Pista ${i + 1}")
            )
        }
        MediaInfoResponse(
            duration = obj.optDouble("duration", 0.0),
            videoCodec = obj.optString("video_codec", ""),
            audioTracks = tracks,
            sig = obj.optString("sig", "")
        )
    }

    /**
     * Firma del archivo para abrirlo fuera de la app (VLC, navegador), que no pueden enviar el token.
     * Vacía si el servidor no tiene token configurado.
     */
    suspend fun fileSignature(filename: String): String =
        if (token.isBlank()) "" else runCatching { fetchMediaInfo(filename).sig }.getOrDefault("")

    // ── URLs de archivos ─────────────────────────

    private fun withSig(url: String, sig: String): String = if (sig.isBlank()) url else "$url?sig=${enc(sig)}"

    fun getDirectStreamUrl(filename: String, sig: String = ""): String = withSig("$baseUrl/stream/${enc(filename)}", sig)

    fun getDownloadUrl(filename: String, sig: String = ""): String = withSig("$baseUrl/download/${enc(filename)}", sig)

    fun getPipeTrackStreamUrl(filename: String, trackIndex: Int, startSec: Double): String =
        "$baseUrl/stream-track/${enc(filename)}?track=$trackIndex&start=${"%.1f".format(java.util.Locale.US, startSec)}"
}
