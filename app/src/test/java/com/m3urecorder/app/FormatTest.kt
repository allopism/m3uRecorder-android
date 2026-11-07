package com.m3urecorder.app

import com.m3urecorder.app.data.AppPrefs
import com.m3urecorder.app.ui.components.hueFor
import com.m3urecorder.app.ui.components.initials
import com.m3urecorder.app.util.countdownText
import com.m3urecorder.app.util.defaultName
import com.m3urecorder.app.util.fmtDay
import com.m3urecorder.app.util.formatBytes
import com.m3urecorder.app.util.humanDuration
import com.m3urecorder.app.util.parseHms
import com.m3urecorder.app.util.secsToHHMMSS
import com.m3urecorder.app.util.shortDuration
import com.m3urecorder.app.util.stripExt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class FormatTest {

    @Test
    fun `la dirección del servidor se normaliza`() {
        assertEquals("http://192.168.1.10:9984", AppPrefs.normalizeUrl(" 192.168.1.10:9984/ "))
        assertEquals("https://dvr.casa.lan", AppPrefs.normalizeUrl("https://dvr.casa.lan/"))
        assertEquals("HTTP://host:1", AppPrefs.normalizeUrl("HTTP://host:1"))
        assertEquals("", AppPrefs.normalizeUrl("   "))
    }

    @Test
    fun `duraciones`() {
        assertEquals("01:01:01", secsToHHMMSS(3661))
        assertEquals("00:00:00", secsToHHMMSS(-5))
        assertEquals("1:02:05", shortDuration(3725))
        assertEquals("2:05", shortDuration(125))
        assertEquals("1 h 30 min", humanDuration(5400))
        assertEquals("2 h", humanDuration(7200))
        assertEquals("15 min", humanDuration(900))
    }

    @Test
    fun `duración escrita a mano`() {
        assertEquals(5400L, parseHms("01:30:00"))
        assertEquals(5400L, parseHms("1:30"))
        assertNull(parseHms("00:00:00"))
        assertNull(parseHms("1:75:00"))
        assertNull(parseHms("hora y media"))
    }

    @Test
    fun `tamaños con formato español`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2 MB", formatBytes(1_572_864))
        assertEquals("4,9 GB", formatBytes(5_304_284_160))
    }

    @Test
    fun `cuenta atrás como la web`() {
        val now = 1_000_000_000L
        assertEquals("Empezando…", countdownText(now - 1, now))
        assertEquals("en 4:09", countdownText(now + 249_000, now))
        assertEquals("en 1 h 5 min", countdownText(now + 3_900_000, now))
        assertEquals("en 2 d 3 h", countdownText(now + (2 * 86400 + 3 * 3600) * 1000L, now))
    }

    @Test
    fun `días relativos`() {
        val today = LocalDate.of(2026, 11, 7)
        val zone = ZoneId.of("Europe/Madrid")
        assertEquals("hoy", fmtDay(today.atTime(21, 0).atZone(zone), today))
        assertEquals("mañana", fmtDay(today.plusDays(1).atTime(9, 0).atZone(zone), today))
        assertEquals("ayer", fmtDay(today.minusDays(1).atTime(9, 0).atZone(zone), today))
    }

    @Test
    fun `nombres de archivo`() {
        val at = LocalDateTime.of(2026, 11, 7, 21, 5)
        assertEquals("Noticias 24 2026-11-07 21h05", defaultName("Noticias 24", at))
        assertEquals("Grabación 2026-11-07 21h05", defaultName(null, at))
        assertEquals("Noticias 24", stripExt("Noticias 24.mp4"))
        assertEquals("sin_extension", stripExt("sin_extension"))
    }

    @Test
    fun `monogramas iguales que en la web`() {
        assertEquals("RJ", initials("Radio Jazz 101"))
        assertEquals("CT", initials("Ciencia TV"))
        assertEquals("TC", initials("The Travel Channel HD"))
        assertEquals("N24", initials("Noticias 24")) // los números se quedan con 2 cifras
        // El mismo nombre siempre da el mismo color
        assertEquals(hueFor("Canal Cocina"), hueFor("Canal Cocina"))
    }
}
