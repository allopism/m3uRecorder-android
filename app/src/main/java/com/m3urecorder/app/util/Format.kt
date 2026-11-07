package com.m3urecorder.app.util

import java.text.Normalizer
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

// Formatos de texto, nombres de archivo y fechas.

fun defaultTimestampFilename(prefix: String): String {
    val now = LocalDateTime.now()
    val tag = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    return "${prefix}_$tag"
}

fun sanitizeChannelFilename(channelName: String, prefix: String): String {
    val norm = Normalizer.normalize(channelName, Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .replace("[^a-zA-Z0-9]+".toRegex(), "_")
        .trim('_')
        .lowercase()
    val nowTag = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
    return "$prefix${norm.ifBlank { "canal" }}_$nowTag"
}

fun secsToHHMMSS(secs: Int): String {
    val s = max(0, secs)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

fun formatBytesHuman(bytes: Long): String {
    var b = bytes.toDouble()
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var i = 0
    while (b >= 1024 && i < units.size - 1) {
        b /= 1024
        i++
    }
    return if (i == 0) "${b.toInt()} B" else "%.2f %s".format(b, units[i])
}

fun formatIsoDisplay(isoStr: String): String {
    return runCatching {
        OffsetDateTime.parse(isoStr).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
    }.getOrDefault(isoStr.take(16))
}
