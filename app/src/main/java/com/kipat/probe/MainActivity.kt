package com.kipat.probe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.kipat.probe.navigation.KipatApp
import com.kipat.probe.nfc.NfcIntentParser
import com.kipat.probe.nfc.NfcIntents
import com.kipat.probe.nfc.NfcWriteController
import com.kipat.probe.ui.theme.KipatTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Root Kipat application shell (Compose). Also hosts the prototype tag writer, whose foreground
 * dispatch is only active while a write is armed from the Touch tab.
 */
class MainActivity : ComponentActivity() {

    private lateinit var writer: NfcWriteController
    private val pendingDestination = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        writer = NfcWriteController(this, lifecycleScope)
        if (savedInstanceState == null) routeIntent(intent)

        setContent {
            KipatTheme {
                KipatApp(writer = writer, pendingDestination = pendingDestination)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        writer.onResume()
    }

    override fun onPause() {
        writer.onPause()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // An armed write consumes the tag; otherwise treat it as navigation/tap routing.
        if (!writer.handleIntent(intent)) routeIntent(intent)
    }

    private fun routeIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_DESTINATION)?.let { pendingDestination.value = it }
        // If Android delivered a Kipat card tap here (e.g. via the AAR fallback), hand it to the Reveal.
        if (NfcIntents.isNfcLaunch(intent) && NfcIntentParser.parse(NfcIntents.uriFrom(intent)) != null) {
            startActivity(
                Intent(intent).setClass(this, TapExperienceActivity::class.java),
            )
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "kipat.nav.destination"
    }
}
