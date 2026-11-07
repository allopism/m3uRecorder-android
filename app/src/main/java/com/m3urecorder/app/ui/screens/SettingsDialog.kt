package com.m3urecorder.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.m3urecorder.app.data.ApiClient
import com.m3urecorder.app.data.AppPrefs
import com.m3urecorder.app.service.BackgroundSync
import com.m3urecorder.app.service.ScheduleAlarms
import com.m3urecorder.app.ui.theme.AppTheme
import com.m3urecorder.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/** Dirección del servidor, token, notificación de grabación y tema. */
@Composable
fun ServerSettingsDialog(
    context: Context,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val c = AppTheme.colors
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(AppPrefs.getServerUrl(context)) }
    var token by remember { mutableStateOf(AppPrefs.getToken(context)) }
    var showToken by remember { mutableStateOf(false) }
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
        containerColor = c.surface,
        title = { Text("Ajustes") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionLabel("Servidor")
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it; testResult = null },
                    label = { Text("Dirección") },
                    placeholder = { Text("http://192.168.1.10:9984") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; testResult = null },
                    label = { Text("Token (APP_TOKEN)") },
                    placeholder = { Text("Vacío si el servidor no usa token") },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                if (showToken) "Ocultar token" else "Mostrar token"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    ) { Text(if (testing) "Probando…" else "Probar conexión") }
                    testResult?.let { (ok, msg) ->
                        Icon(
                            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null,
                            tint = if (ok) c.ok else c.rec, modifier = Modifier.size(18.dp)
                        )
                        Text(
                            msg.removeSuffix(" (revisa Ajustes)"),
                            color = if (ok) c.ok else c.rec,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(color = c.border)
                SectionLabel("Aspecto")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro")
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { onThemeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            icon = {},
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = c.accentSoft,
                                activeContentColor = c.accent,
                                inactiveContainerColor = c.surface,
                                inactiveContentColor = c.text2
                            )
                        ) { Text(label) }
                    }
                }

                HorizontalDivider(color = c.border)
                SectionLabel("Notificaciones")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Grabación en curso", style = MaterialTheme.typography.bodyLarge, color = c.text)
                        Text(
                            "Con cuenta atrás y botón Detener. Los avisos de completada o fallida llegan por Telegram.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.text2
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Switch(checked = liveNotif, onCheckedChange = { liveNotif = it })
                }
                // Sin este permiso, la notificación de una programada puede salir hasta 10 min tarde
                if (liveNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmsOk) {
                    Text(
                        "Para que las programadas avisen a su hora exacta, permite «Alarmas y recordatorios».",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.warn
                    )
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }) { Text("Permitir alarmas exactas") }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = serverUrl.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                onClick = {
                    AppPrefs.setServerUrl(context, serverUrl)
                    AppPrefs.setToken(context, token)
                    AppPrefs.setLiveNotificationEnabled(context, liveNotif)
                    BackgroundSync.configure(context)
                    Toast.makeText(context, "Ajustes guardados", Toast.LENGTH_SHORT).show()
                    onSaved()
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.text3)
}
