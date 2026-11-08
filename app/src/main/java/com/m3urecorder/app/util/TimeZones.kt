package com.m3urecorder.app.util

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/*
 * Cambios de hora (horario de verano).
 *
 * - En octubre, la hora de 02:00 a 03:00 ocurre DOS veces (primero en verano, +02:00,
 *   y otra vez en invierno, +01:00). Al programar a esa hora hay que elegir cuál.
 * - En marzo, de 02:00 se salta a 03:00: esas horas NO existen.
 *
 * Igual que en la web: "fold" 0 = primera vez (verano), 1 = segunda vez (invierno).
 */

/** Desfases válidos para una hora local: 1 normalmente, 2 si se repite, 0 si no existe. */
fun validOffsets(dt: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): List<ZoneOffset> =
    zone.rules.getValidOffsets(dt)

/** true si esa hora ocurre dos veces (cambio de hora de octubre). */
fun isRepeatedHour(dt: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    validOffsets(dt, zone).size == 2

/** true si esa hora no existe (cambio de hora de marzo). */
fun isSkippedHour(dt: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    validOffsets(dt, zone).isEmpty()

/**
 * Instante exacto → hora local y "fold" (cuál de las dos veces, si esa hora se repite).
 * Es la operación inversa de [toOffsetDateTime].
 */
fun toLocalWithFold(instant: OffsetDateTime, zone: ZoneId = ZoneId.systemDefault()): Pair<LocalDateTime, Int> {
    val local = instant.atZoneSameInstant(zone).toLocalDateTime()
    val offsets = validOffsets(local, zone)
    val fold = if (offsets.size == 2 && instant.atZoneSameInstant(zone).offset == offsets[1]) 1 else 0
    return local to fold
}

/**
 * Hora local → instante exacto con su desfase, eligiendo la 1.ª o 2.ª vez si se repite.
 * Si la hora no existe, Java la adelanta (02:30 → 03:30), igual que haría un reloj.
 */
fun toOffsetDateTime(dt: LocalDateTime, fold: Int, zone: ZoneId = ZoneId.systemDefault()): OffsetDateTime {
    val offsets = validOffsets(dt, zone)
    return if (offsets.isEmpty()) dt.atZone(zone).toOffsetDateTime()
    else dt.atOffset(offsets[fold.coerceIn(0, offsets.size - 1)])
}
