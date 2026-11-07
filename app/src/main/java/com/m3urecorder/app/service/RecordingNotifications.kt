package com.m3urecorder.app.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.m3urecorder.app.MainActivity
import com.m3urecorder.app.R
import com.m3urecorder.app.data.ApiClient
import com.m3urecorder.app.data.AppPrefs
import com.m3urecorder.app.data.ScheduleItem
import com.m3urecorder.app.data.ServerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/*
 * Notificación "Grabando ahora".
 *
 * En vez de un servicio en primer plano que pregunta al servidor cada pocos segundos
 * (Android lo bloquea desde segundo plano y gasta batería), se publica UNA notificación
 * normal con cuenta atrás: el reloj lo mueve el propio sistema y la notificación se
 * borra sola al terminar (setTimeoutAfter). La app no necesita estar despierta.
 *
 * Cómo se entera la app de que hay una grabación:
 *  - Con la app abierta: el sondeo de estado de la pantalla principal.
 *  - Programadas: una alarma local a la hora de inicio (las horas ya se conocen).
 *  - Además, una comprobación periódica de WorkManager (cada 15 min, solo en Wi-Fi o VPN).
 */

/** true si el móvil está en Wi-Fi, Ethernet o con una VPN (WireGuard) activa: única forma de llegar al servidor. */
object NetworkTransportHelper {
    fun isServerTransportAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}

object RecordingNotifier {
    private const val CHANNEL_ID = "m3u_recording_now"
    private const val NOTIF_ID = 1001
    private val LEGACY_CHANNELS = listOf("m3u_live_status_channel", "m3u_events_alert_channel")
    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        LEGACY_CHANNELS.forEach { nm.deleteNotificationChannel(it) }
        val channel = NotificationChannel(CHANNEL_ID, "Grabación en curso", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Muestra la grabación activa con el tiempo restante"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun canNotify(context: Context): Boolean {
        if (!AppPrefs.isLiveNotificationEnabled(context)) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Publica la notificación. [key] identifica la grabación: si ya se publicó para esa misma
     * grabación no se repite (así, si el usuario la descarta, no vuelve a aparecer).
     */
    fun show(context: Context, key: String, title: String, scheduled: Boolean, endMillis: Long) {
        if (!canNotify(context)) return
        val now = System.currentTimeMillis()
        if (endMillis <= now || AppPrefs.getPostedRecordingKey(context) == key) return
        ensureChannel(context)

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getBroadcast(
            context, 1, Intent(context, StopRecordingReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val endText = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault()).format(TIME_FMT)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFE03131.toInt()) // rojo de la marca (como el icono y la web)
            .setContentTitle("Grabando · $title")
            .setContentText("${if (scheduled) "Programada" else "Al momento"} · termina a las $endText")
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp)
            .addAction(0, "Detener", stop)
            // Cuenta atrás hasta el final, movida por el sistema
            .setWhen(endMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(endMillis - now + 5_000)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification)
        AppPrefs.setPostedRecordingKey(context, key)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        AppPrefs.setPostedRecordingKey(context, "")
    }

    /** Ajusta la notificación al estado real del servidor. */
    fun syncFromStatus(context: Context, status: ServerStatus) {
        if (!status.running) {
            if (AppPrefs.getPostedRecordingKey(context).isNotEmpty()) cancel(context)
            return
        }
        val start = status.startTime ?: return
        val duration = status.durationRequested ?: return
        val startMillis = runCatching { OffsetDateTime.parse(start).toInstant().toEpochMilli() }.getOrNull() ?: return
        val title = status.channel.ifBlank { status.filename ?: "grabación" }
        show(context, "rec:$start", title, status.mode == "scheduled", startMillis + duration * 1000L)
    }
}

/** Alarmas locales a la hora de inicio de cada programada pendiente. */
object ScheduleAlarms {
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_END = "end_millis"

    private fun pendingIntent(context: Context, id: String, title: String = "", endMillis: Long = 0L, create: Boolean = true): PendingIntent? {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java)
            .setData(Uri.parse("m3urecorder://schedule/$id")) // una alarma distinta por programación
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_END, endMillis)
        val flags = PendingIntent.FLAG_IMMUTABLE or (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    /** Deja una alarma por cada programada pendiente y quita las de las que ya no lo están. */
    fun sync(context: Context, schedules: List<ScheduleItem>) {
        val am = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val wanted = mutableSetOf<String>()

        if (AppPrefs.isLiveNotificationEnabled(context)) {
            for (job in schedules) {
                if (job.status != "pending") continue
                val start = parseMillis(job.startTime) ?: continue
                val end = parseMillis(job.endTime) ?: continue
                if (start <= now) continue
                val title = job.channel.ifBlank { job.filename }
                val pi = pendingIntent(context, job.id, title, end)!!
                val at = start + 5_000
                if (canUseExactAlarms(context)) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    // Sin el permiso "Alarmas y recordatorios", Android admite un margen: lo limitamos a 10 min
                    am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi)
                }
                wanted += job.id
            }
        }
        (AppPrefs.getScheduledAlarmIds(context) - wanted).forEach { id ->
            pendingIntent(context, id, create = false)?.let { am.cancel(it); it.cancel() }
        }
        AppPrefs.setScheduledAlarmIds(context, wanted)
    }

    fun cancelAll(context: Context) = sync(context, emptyList())

    /** En Android 12+ las alarmas exactas requieren el permiso "Alarmas y recordatorios". */
    fun canUseExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun parseMillis(iso: String): Long? = runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()

    internal fun titleFrom(intent: Intent): String = intent.getStringExtra(EXTRA_TITLE).orEmpty()
    internal fun endFrom(intent: Intent): Long = intent.getLongExtra(EXTRA_END, 0L)
}

/** Salta a la hora de inicio de una programada. */
class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (NetworkTransportHelper.isServerTransportAvailable(context)) {
                    // Si llegamos al servidor, mandan los datos reales (por si se canceló o falló)
                    val status = runCatching { ApiClient(context).fetchStatus(timeoutMs = 4000) }.getOrNull()
                    if (status != null) {
                        RecordingNotifier.syncFromStatus(context, status)
                        return@launch
                    }
                }
                // Sin conexión con el servidor: se muestra con los datos de la programación
                val id = intent.data?.lastPathSegment ?: return@launch
                RecordingNotifier.show(context, "sched:$id", ScheduleAlarms.titleFrom(intent), true, ScheduleAlarms.endFrom(intent))
            } finally {
                pending.finish()
            }
        }
    }
}

/** Botón "Detener" de la notificación. */
class StopRecordingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient(context).stopRecording().onSuccess { RecordingNotifier.cancel(context) }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Comprobación periódica en segundo plano (cada 15 min, el mínimo de Android). */
class ServerSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!AppPrefs.isLiveNotificationEnabled(ctx) || !NetworkTransportHelper.isServerTransportAvailable(ctx)) {
            return Result.success()
        }
        val api = ApiClient(ctx)
        if (!api.isConfigured) return Result.success()
        runCatching { RecordingNotifier.syncFromStatus(ctx, api.fetchStatus()) }
        runCatching { ScheduleAlarms.sync(ctx, api.fetchSchedules()) }
        return Result.success()
    }
}

object BackgroundSync {
    private const val WORK_NAME = "m3u_periodic_sync"

    /** Activa o desactiva la comprobación periódica según los ajustes. */
    fun configure(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (!AppPrefs.isLiveNotificationEnabled(context)) {
            wm.cancelUniqueWork(WORK_NAME)
            ScheduleAlarms.cancelAll(context)
            RecordingNotifier.cancel(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<ServerSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
