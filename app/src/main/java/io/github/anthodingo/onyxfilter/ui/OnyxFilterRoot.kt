package io.github.anthodingo.onyxfilter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.anthodingo.onyxfilter.data.AuthState
import io.github.anthodingo.onyxfilter.data.OnyxFilterRepository
import io.github.anthodingo.onyxfilter.ui.login.LoginScreen
import io.github.anthodingo.onyxfilter.ui.login.LoginViewModel
import io.github.anthodingo.onyxfilter.ui.pairing.PairingDialog
import io.github.anthodingo.onyxfilter.ui.pairing.PairingViewModel
import io.github.anthodingo.onyxfilter.ui.protection.ProtectionScreen
import io.github.anthodingo.onyxfilter.ui.protection.ProtectionViewModel

/** Écran de connexion ou de gestion de la protection, selon l'état de la session. */
@Composable
fun OnyxFilterRoot(repository: OnyxFilterRepository, pairingViewModel: PairingViewModel) {
    val authState by repository.authState.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = authState) {
            AuthState.Restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is AuthState.LoggedOut -> LoginScreen(
                viewModel = viewModel(factory = LoginViewModel.Factory),
                tokenRejected = state.tokenRejected,
            )

            is AuthState.LoggedIn -> ProtectionScreen(
                session = state.session,
                // Un ViewModel par instance : rien n'est repris d'un serveur à l'autre.
                viewModel = viewModel(
                    key = "protection:${state.session.serverUrl}",
                    factory = ProtectionViewModel.Factory,
                ),
            )
        }

        PairingDialog(pairingViewModel)
    }
}
