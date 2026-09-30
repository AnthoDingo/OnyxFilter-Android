package io.github.anthodingo.onyxfilter.ui.protection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.domain.DisableDuration

/** Saisie d'une durée de désactivation en heures et minutes (30 jours au plus). */
@Composable
fun CustomDurationDialog(
    onConfirm: (DisableDuration) -> Unit,
    onDismiss: () -> Unit,
) {
    var hours by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("30") }
    val duration = DisableDuration.custom(hours.toLongOrNull() ?: 0, minutes.toLongOrNull() ?: 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_duration_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.digitsOnly(MAX_HOURS_DIGITS) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.custom_duration_hours)) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.digitsOnly(MAX_MINUTES_DIGITS) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.custom_duration_minutes)) },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    )
                }
                Text(
                    text = stringResource(R.string.custom_duration_limits),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (duration == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { duration?.let(onConfirm) }, enabled = duration != null) {
                Text(stringResource(R.string.custom_duration_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun String.digitsOnly(maxLength: Int): String = filter(Char::isDigit).take(maxLength)

private const val MAX_HOURS_DIGITS = 3
private const val MAX_MINUTES_DIGITS = 5
