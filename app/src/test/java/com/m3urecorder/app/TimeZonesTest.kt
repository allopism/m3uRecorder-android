package com.m3urecorder.app

import com.m3urecorder.app.util.isRepeatedHour
import com.m3urecorder.app.util.isSkippedHour
import com.m3urecorder.app.util.toLocalWithFold
import com.m3urecorder.app.util.toOffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/** Cambios de hora de 2026 en España: 29 de marzo (se salta 02:00–03:00) y 25 de octubre (se repite). */
class TimeZonesTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val octoberRepeated = LocalDateTime.of(2026, 10, 25, 2, 30)
    private val marchSkipped = LocalDateTime.of(2026, 3, 29, 2, 30)

    @Test
    fun `detecta la hora repetida de octubre y la inexistente de marzo`() {
        assertTrue(isRepeatedHour(octoberRepeated, madrid))
        assertTrue(isSkippedHour(marchSkipped, madrid))
        val normal = LocalDateTime.of(2026, 11, 8, 21, 0)
        assertFalse(isRepeatedHour(normal, madrid))
        assertFalse(isSkippedHour(normal, madrid))
    }

    @Test
    fun `primera vez es verano y segunda vez invierno`() {
        assertEquals("2026-10-25T02:30+02:00", toOffsetDateTime(octoberRepeated, 0, madrid).toString())
        assertEquals("2026-10-25T02:30+01:00", toOffsetDateTime(octoberRepeated, 1, madrid).toString())
    }

    @Test
    fun `la duración real cuenta la hora repetida`() {
        // De 01:00 a 04:00 la noche del cambio de octubre pasan 4 horas reales, no 3
        val start = toOffsetDateTime(LocalDateTime.of(2026, 10, 25, 1, 0), 0, madrid)
        val end = toOffsetDateTime(LocalDateTime.of(2026, 10, 25, 4, 0), 0, madrid)
        assertEquals(4L, Duration.between(start, end).toHours())
        // Y de 02:30 (1.ª vez) a 02:30 (2.ª vez) pasa exactamente una hora
        val first = toOffsetDateTime(octoberRepeated, 0, madrid)
        val second = toOffsetDateTime(octoberRepeated, 1, madrid)
        assertEquals(3600L, Duration.between(first, second).seconds)
    }

    @Test
    fun `de instante a hora local con su fold`() {
        // 1 h real después de las 02:30 (1.ª vez) son las 02:30 otra vez, pero la 2.ª
        val first = toOffsetDateTime(octoberRepeated, 0, madrid)
        assertEquals(octoberRepeated to 1, toLocalWithFold(first.plusHours(1), madrid))
        assertEquals(octoberRepeated to 0, toLocalWithFold(first, madrid))
    }

    @Test
    fun `una hora que no existe se adelanta`() {
        assertEquals("2026-03-29T03:30+02:00", toOffsetDateTime(marchSkipped, 0, madrid).toString())
    }
}
