package io.github.anthodingo.onyxfilter.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.ui.UiText
import io.github.anthodingo.onyxfilter.ui.asString
import io.github.anthodingo.onyxfilter.ui.theme.OnyxFilterTheme

@Composable
fun LoginScreen(viewModel: LoginViewModel, sessionExpired: Boolean) {
    LoginContent(
        state = viewModel.uiState,
        sessionExpired = sessionExpired,
        onServerUrlChange = viewModel::onServerUrlChange,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::login,
    )
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    sessionExpired: Boolean,
    onServerUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Inclut le clavier : le formulaire défile au-dessus de celui-ci.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_shield_check),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sessionExpired) {
                MessageCard(UiText.Resource(R.string.login_session_expired), isError = false)
            }

            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = onServerUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.login_server_url)) },
                placeholder = { Text(stringResource(R.string.login_server_url_placeholder)) },
                supportingText = {
                    // Avertissement, pas une erreur : HTTP reste permis pour une instance locale.
                    if (state.isCleartext) {
                        Text(stringResource(R.string.login_server_url_cleartext), color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(stringResource(R.string.login_server_url_help))
                    }
                },
                singleLine = true,
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
            )

            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Username },
                label = { Text(stringResource(R.string.login_username)) },
                singleLine = true,
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Password },
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                enabled = !state.isLoading,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            painter = painterResource(
                                if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                            ),
                            contentDescription = stringResource(
                                if (passwordVisible) R.string.login_hide_password else R.string.login_show_password,
                            ),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onSubmit()
                    },
                ),
            )

            state.error?.let { MessageCard(it, isError = true) }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSubmit()
                },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.login_in_progress))
                    }
                } else {
                    Text(stringResource(R.string.login_submit))
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: UiText, isError: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(
            text = message.asString(),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    OnyxFilterTheme {
        LoginContent(
            state = LoginUiState(
                serverUrl = "http://192.168.1.10:5259",
                username = "admin",
                error = UiText.Resource(R.string.error_invalid_credentials),
            ),
            sessionExpired = true,
            onServerUrlChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}
