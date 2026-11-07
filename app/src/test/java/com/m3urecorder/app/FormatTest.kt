package com.m3urecorder.app

import com.m3urecorder.app.data.AppPrefs
import com.m3urecorder.app.util.formatBytesHuman
import com.m3urecorder.app.util.formatIsoDisplay
import com.m3urecorder.app.util.sanitizeChannelFilename
import com.m3urecorder.app.util.secsToHHMMSS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FormatTest {

    private lateinit var previousLocale: Locale

    // Los decimales dependen del idioma del sistema: se fija para que la prueba sea estable
    @Before
    fun fixLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() = Locale.setDefault(previousLocale)

    @Test
    fun `la dirección del servidor se normaliza`() {
        assertEquals("http://192.168.1.10:9984", AppPrefs.normalizeUrl(" 192.168.1.10:9984/ "))
        assertEquals("https://dvr.casa.lan", AppPrefs.normalizeUrl("https://dvr.casa.lan/"))
        assertEquals("HTTP://host:1", AppPrefs.normalizeUrl("HTTP://host:1"))
        assertEquals("", AppPrefs.normalizeUrl("   "))
    }

    @Test
    fun `segundos a HH MM SS`() {
        assertEquals("00:00:00", secsToHHMMSS(0))
        assertEquals("01:01:01", secsToHHMMSS(3661))
        assertEquals("00:00:00", secsToHHMMSS(-5))
        assertEquals("27:46:40", secsToHHMMSS(100_000))
    }

    @Test
    fun `tamaños legibles`() {
        assertEquals("512 B", formatBytesHuman(512))
        assertEquals("1.00 KB", formatBytesHuman(1024))
        assertEquals("1.50 MB", formatBytesHuman(1_572_864))
        assertEquals("4.94 GB", formatBytesHuman(5_304_284_160))
    }

    @Test
    fun `nombre de archivo a partir del canal`() {
        val name = sanitizeChannelFilename("Música Live · HD!", prefix = "prog_")
        assertTrue(name, name.matches(Regex("prog_musica_live_hd_\\d{8}_\\d{4}")))
        assertTrue(sanitizeChannelFilename("¡¿?!", prefix = "").startsWith("canal_"))
    }

    @Test
    fun `fechas del servidor`() {
        // Se muestra la hora tal como la envía el servidor (su hora local)
        assertEquals("07/11 22:29", formatIsoDisplay("2026-11-07T22:29:17+01:00"))
        // Si no es una fecha válida se recorta en vez de fallar
        assertEquals("no es una fecha", formatIsoDisplay("no es una fecha"))
    }
}
