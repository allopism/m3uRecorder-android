package com.m3urecorder.app.util

import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

// Formatos de texto, nombres de archivo y fechas (mismos textos que la web).

private val SPANISH = Locale.forLanguageTag("es-ES")
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_FMT = DateTimeFormatter.ofPattern("EEE d MMM", SPANISH)
private val NAME_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH'h'mm")

private fun pad2(n: Long) = n.toString().padStart(2, '0')

/** 3725 → "01:02:05" (para la cuenta de una grabación en curso). */
fun secsToHHMMSS(secs: Int): String {
    val s = max(0, secs)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

/** Duración corta para las miniaturas: 3725 → "1:02:05", 125 → "2:05". */
fun shortDuration(secs: Int): String {
    val s = max(0, secs).toLong()
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "$h:${pad2(m)}:${pad2(s % 60)}" else "$m:${pad2(s % 60)}"
}

/** Duración legible: 5400 → "1 h 30 min", 900 → "15 min". */
fun humanDuration(secs: Long): String {
    val s = max(0L, secs)
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        m > 0 -> "$m min"
        else -> "${s % 60} s"
    }
}

/** Tamaño legible con formato español: 5 304 284 160 → "4,9 GB". */
fun formatBytes(bytes: Long): String {
    var b = max(0L, bytes).toDouble()
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var i = 0
    while (b >= 1024 && i < units.size - 1) {
        b /= 1024
        i++
    }
    val nf = NumberFormat.getNumberInstance(SPANISH).apply { maximumFractionDigits = if (i >= 3) 1 else 0 }
    return "${nf.format(b)} ${units[i]}"
}

/** Cuenta atrás hasta una hora: "en 2 d 3 h", "en 1 h 5 min", "en 4:09" o "Empezando…". */
fun countdownText(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val secs = (targetMillis - nowMillis) / 1000
    if (secs <= 0) return "Empezando…"
    val d = secs / 86400
    val h = (secs % 86400) / 3600
    val m = (secs % 3600) / 60
    return when {
        d > 0 -> "en $d d $h h"
        h > 0 -> "en $h h $m min"
        else -> "en $m:${pad2(secs % 60)}"
    }
}

/** Convierte una fecha ISO del servidor a la zona del móvil (null si no es válida). */
fun parseIso(iso: String?): ZonedDateTime? =
    iso?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull() }

fun epochMillis(iso: String?): Long? = parseIso(iso)?.toInstant()?.toEpochMilli()

fun fmtTime(dt: ZonedDateTime): String = dt.format(TIME_FMT)

/** "hoy", "mañana", "ayer" o "sáb 8 nov". */
fun fmtDay(dt: ZonedDateTime, today: LocalDate = LocalDate.now()): String = when (dt.toLocalDate()) {
    today -> "hoy"
    today.plusDays(1) -> "mañana"
    today.minusDays(1) -> "ayer"
    else -> dt.format(DAY_FMT).replace(".", "")
}

fun fmtDay(dt: LocalDateTime): String = fmtDay(dt.atZone(ZoneId.systemDefault()))

/** Fecha ISO del servidor para mostrar: "hoy, 21:00". */
fun formatIsoDisplay(isoStr: String): String =
    parseIso(isoStr)?.let { "${fmtDay(it)}, ${fmtTime(it)}" } ?: isoStr.take(16)

/** Nombre propuesto para una grabación: "Noticias 24 2026-11-07 21h00" (como la web). */
fun defaultName(channelName: String?, now: LocalDateTime = LocalDateTime.now()): String =
    "${channelName?.takeIf { it.isNotBlank() } ?: "Grabación"} ${now.format(NAME_STAMP)}"

/** "Noticias 24.mp4" → "Noticias 24". */
fun stripExt(filename: String): String {
    val dot = filename.lastIndexOf('.')
    return if (dot > 0) filename.substring(0, dot) else filename
}

/** "HH:MM:SS" o "HH:MM" → segundos (null si no es válido o es cero). */
fun parseHms(text: String): Long? {
    val parts = text.trim().split(":")
    if (parts.size !in 2..3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
    val nums = parts.map { it.toLong() }
    val secs = if (nums.size == 3) nums[0] * 3600 + nums[1] * 60 + nums[2] else nums[0] * 3600 + nums[1] * 60
    if (nums.drop(1).any { it >= 60 }) return null
    return secs.takeIf { it > 0 }
}

fun hmsFromSeconds(secs: Long): String = secsToHHMMSS(secs.toInt())

fun minutesBetween(a: LocalDateTime, b: LocalDateTime): Long = Duration.between(a, b).toMinutes()
