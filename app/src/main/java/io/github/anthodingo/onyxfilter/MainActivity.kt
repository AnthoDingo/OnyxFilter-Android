package io.github.anthodingo.onyxfilter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.anthodingo.onyxfilter.ui.OnyxFilterRoot
import io.github.anthodingo.onyxfilter.ui.pairing.PairingViewModel
import io.github.anthodingo.onyxfilter.ui.theme.OnyxFilterTheme

class MainActivity : ComponentActivity() {

    private val pairingViewModel: PairingViewModel by viewModels { PairingViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Après une rotation, le lien a déjà été traité (son jeton est à usage unique).
        if (savedInstanceState == null) handlePairingIntent(intent)

        val repository = (application as OnyxFilterApplication).repository
        setContent {
            OnyxFilterTheme {
                OnyxFilterRoot(repository, pairingViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePairingIntent(intent)
    }

    // Lien onyxfilter://pair du QR code de la page « Accès API ».
    private fun handlePairingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) pairingViewModel.onLink(intent.dataString)
    }
}
