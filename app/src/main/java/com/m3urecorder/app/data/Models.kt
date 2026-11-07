package com.m3urecorder.app.data

import androidx.compose.runtime.Immutable

@Immutable
data class VpnInfo(
    // Falso hasta saber del servidor: así el botón de VPN no aparece antes de conectar
    val enabled: Boolean = false,
    val available: Boolean = false,
    val status: String = "unknown",
    val publicIp: String = "",
    val ipCountry: String = "",
    val configuredCountry: String = "",
    val switching: Boolean = false
) {
    val activeCountryDisplay: String
        get() = configuredCountry.ifBlank { ipCountry.ifBlank { "VPN" } }
}

@Immutable
data class LastResultInfo(
    val status: String = "",
    val reason: String = ""
)

@Immutable
data class ServerStatus(
    val running: Boolean = false,
    val progress: String = "Listo para grabar",
    val startTime: String? = null,
    val durationRequested: Int? = null,
    val filename: String? = null,
    val channel: String = "",
    val logoId: String = "",
    val mode: String? = null,
    val vpnCountry: String? = null,
    val serverTime: String = "",
    val schedulesPending: Int = 0,
    val historyCount: Int = 0,
    val vpn: VpnInfo = VpnInfo(),
    val lastResult: LastResultInfo? = null
)

@Immutable
data class VpnCountryOption(
    val value: String,
    val label: String
)

@Immutable
data class VpnCountriesResponse(
    val featured: List<VpnCountryOption> = emptyList(),
    val others: List<String> = emptyList()
)

@Immutable
data class ChannelItem(
    val name: String,
    val url: String,
    val group: String = "",
    val logoId: String = ""
)

@Immutable
data class ScheduleItem(
    val id: String,
    val url: String,
    val filename: String,
    val channel: String = "",
    val logoId: String = "",
    val format: String,
    val startTime: String,
    val endTime: String,
    val durationSeconds: Int,
    val vpnCountry: String = "",
    val status: String = "pending",
    val statusReason: String = "",
    val filesizeHuman: String = ""
)

@Immutable
data class HistoryItem(
    val filename: String,
    val url: String,
    val channel: String = "",
    val logoId: String = "",
    val start: String,
    val end: String,
    val durationRequested: String,
    val durationSeconds: Int,
    val mediaDurationSeconds: Int,
    val format: String,
    val filesizeBytes: Long,
    val filesizeHuman: String,
    val status: String,
    val statusReason: String,
    val vpnCountry: String = "",
    val type: String = "immediate"
)

@Immutable
data class AudioTrackInfo(
    val index: Int,
    val codec: String = "",
    val lang: String = "",
    val label: String
)

@Immutable
data class MediaInfoResponse(
    val duration: Double = 0.0,
    val videoCodec: String = "",
    val audioTracks: List<AudioTrackInfo> = emptyList(),
    val sig: String = ""
)

