package com.m3urecorder.app.data

import android.content.Context

/** Ajustes guardados en el móvil (dirección, token y estado de las notificaciones). */
object AppPrefs {
    private const val PREFS_NAME = "m3u_recorder_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_TOKEN = "app_token"
    private const val KEY_LIVE_NOTIF_ENABLED = "bg_notifs_enabled"
    private const val KEY_POSTED_RECORDING = "posted_recording_key"
    private const val KEY_ALARM_IDS = "schedule_alarm_ids"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Dirección del servidor sin barra final. Vacía hasta que el usuario la configura. */
    fun getServerUrl(context: Context): String = normalizeUrl(prefs(context).getString(KEY_SERVER_URL, "") ?: "")

    fun setServerUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_SERVER_URL, normalizeUrl(url)).apply()
    }

    fun getToken(context: Context): String = prefs(context).getString(KEY_TOKEN, "")?.trim() ?: ""

    fun setToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun isLiveNotificationEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_LIVE_NOTIF_ENABLED, true)

    fun setLiveNotificationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LIVE_NOTIF_ENABLED, enabled).apply()
    }

    /** Grabación para la que ya se publicó la notificación (para no repetirla si el usuario la descarta). */
    fun getPostedRecordingKey(context: Context): String = prefs(context).getString(KEY_POSTED_RECORDING, "") ?: ""

    fun setPostedRecordingKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_POSTED_RECORDING, key).apply()
    }

    fun getScheduledAlarmIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ALARM_IDS, emptySet())?.toSet() ?: emptySet()

    fun setScheduledAlarmIds(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(KEY_ALARM_IDS, ids).apply()
    }

    /** Acepta "192.168.1.10:9984" y añade http:// si falta. */
    fun normalizeUrl(raw: String): String {
        val clean = raw.trim().trimEnd('/')
        if (clean.isEmpty()) return ""
        return if (clean.startsWith("http://", true) || clean.startsWith("https://", true)) clean else "http://$clean"
    }
}
