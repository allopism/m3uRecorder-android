package com.m3urecorder.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.m3urecorder.app.data.*
import com.m3urecorder.app.service.BackgroundSync
import com.m3urecorder.app.service.ScheduleAlarms
import com.m3urecorder.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun ServerSettingsDialog(
    context: Context,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(AppPrefs.getServerUrl(context)) }
    var token by remember { mutableStateOf(AppPrefs.getToken(context)) }
    var liveNotif by remember { mutableStateOf(AppPrefs.isLiveNotificationEnabled(context)) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var testing by remember { mutableStateOf(false) }
    // Se vuelve a comprobar al regresar de los ajustes del sistema
    var exactAlarmsOk by remember { mutableStateOf(ScheduleAlarms.canUseExactAlarms(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) exactAlarmsOk = ScheduleAlarms.canUseExactAlarms(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text("⚙️ Ajustes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it; testResult = null },
                    label = { Text("Dirección del servidor") },
                    placeholder = { Text("http://192.168.1.10:9984") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; testResult = null },
                    label = { Text("Token (APP_TOKEN)") },
                    placeholder = { Text("Vacío si el servidor no usa token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        enabled = serverUrl.isNotBlank() && !testing,
                        onClick = {
                            testing = true
                            scope.launch {
                                val result = ApiClient(context, serverUrl, token).testConnection()
                                testResult = result.fold({ true to it }, { false to (it.message ?: "Error") })
                                testing = false
                            }
                        }
                    ) { Text(if (testing) "Probando…" else "Probar conexión", fontSize = 12.sp) }

                    testResult?.let { (ok, msg) ->
                        Text(
                            text = (if (ok) "✓ " else "✕ ") + msg,
                            color = if (ok) AccentEmerald else Color(0xFFFDA4AF),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notificación de grabación en curso", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                        Text(
                            "Con cuenta atrás y botón Detener. Los avisos de completada o fallida llegan por Telegram.",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Switch(checked = liveNotif, onCheckedChange = { liveNotif = it })
                }

                // Sin este permiso, la notificación de una programada puede salir hasta 10 min tarde
                if (liveNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmsOk) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Para que las programadas avisen a su hora exacta, permite \"Alarmas y recordatorios\".",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                                )
                            }
                        }) { Text("Permitir alarmas exactas", fontSize = 12.sp) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = serverUrl.isNotBlank(),
                onClick = {
                    AppPrefs.setServerUrl(context, serverUrl)
                    AppPrefs.setToken(context, token)
                    AppPrefs.setLiveNotificationEnabled(context, liveNotif)
                    BackgroundSync.configure(context)
                    Toast.makeText(context, "✅ Ajustes guardados", Toast.LENGTH_SHORT).show()
                    onSaved()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Guardar", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}
