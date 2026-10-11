package io.github.anthodingo.onyxfilter.ui.protection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.domain.DisableDuration

/**
 * Choix de la durée d'une désactivation temporaire. Rien n'est fait tant que l'utilisateur n'a pas
 * validé : le retour arrière, un toucher hors de la fenêtre ou "Annuler" abandonnent l'action.
 *
 * @param onConfirm appelé avec la durée choisie parmi [DisableDuration.Presets].
 * @param onCustomDuration appelé si l'utilisateur valide "Durée personnalisée…".
 */
@Composable
fun TemporaryDisableDialog(
    onConfirm: (DisableDuration) -> Unit,
    onCustomDuration: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Indice dans [DisableDuration.Presets], [CUSTOM] pour la durée personnalisée, [NONE] sans choix.
    var selected by rememberSaveable { mutableStateOf(NONE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.temporary_disable_title)) },
        text = {
            Column(
                modifier = Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
            ) {
                DisableDuration.Presets.forEachIndexed { index, duration ->
                    DurationOption(durationLabel(duration), selected == index) { selected = index }
                }
                DurationOption(stringResource(R.string.disable_custom), selected == CUSTOM) { selected = CUSTOM }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (selected) {
                        CUSTOM -> onCustomDuration()
                        NONE -> Unit
                        else -> onConfirm(DisableDuration.Presets[selected])
                    }
                },
                enabled = selected != NONE,
            ) {
                Text(stringResource(R.string.temporary_disable_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun DurationOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Text(label)
    }
}

private const val NONE = -1
private const val CUSTOM = -2
