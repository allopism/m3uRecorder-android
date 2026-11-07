package com.m3urecorder.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.m3urecorder.app.service.BackgroundSync
import com.m3urecorder.app.ui.M3URecorderApp
import com.m3urecorder.app.ui.MainViewModel
import com.m3urecorder.app.ui.theme.BgBase
import com.m3urecorder.app.ui.theme.M3UTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Enlace recibido desde el menú "Compartir" de otra app, pendiente de procesar. */
    private var sharedStreamUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        BackgroundSync.configure(this)

        setContent {
            M3UTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
                    M3URecorderApp(
                        vm = viewModel,
                        incomingSharedUrl = sharedStreamUrl,
                        onConsumedSharedUrl = { sharedStreamUrl = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (sharedText.isNullOrBlank()) return
        // Se queda con la primera URL del texto compartido (o el texto entero si no hay)
        sharedStreamUrl = Regex("https?://\\S+").find(sharedText)?.value ?: sharedText
    }
}
