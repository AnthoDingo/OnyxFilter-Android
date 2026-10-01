package io.github.anthodingo.onyxfilter.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.data.ServerUrl
import io.github.anthodingo.onyxfilter.ui.asString

/** Dialogues de la connexion par QR code, affichés par-dessus l'écran courant. */
@Composable
fun PairingDialog(viewModel: PairingViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        PairingState.Idle -> Unit

        is PairingState.ConfirmReplace -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.pairing_replace_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.pairing_replace_message,
                        ServerUrl.displayName(s.currentServerUrl),
                        ServerUrl.displayName(s.link.serverUrl),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmReplace) { Text(stringResource(R.string.pairing_replace_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )

        is PairingState.Connecting -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.pairing_title)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.pairing_connecting, ServerUrl.displayName(s.link.serverUrl)))
                }
            },
            confirmButton = {},
        )

        is PairingState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.pairing_failed_title)) },
            text = { Text(s.error.asString() + "\n\n" + stringResource(R.string.pairing_rescan)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismiss) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}
