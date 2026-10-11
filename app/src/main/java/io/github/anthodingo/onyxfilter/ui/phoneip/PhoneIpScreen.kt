package io.github.anthodingo.onyxfilter.ui.phoneip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.domain.PhoneIpAccess
import io.github.anthodingo.onyxfilter.ui.MenuButton
import io.github.anthodingo.onyxfilter.ui.UiText
import io.github.anthodingo.onyxfilter.ui.asString
import io.github.anthodingo.onyxfilter.ui.theme.OnyxFilterTheme

@Composable
fun PhoneIpScreen(viewModel: PhoneIpViewModel, onOpenMenu: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message.asString(context)) }
    }

    PhoneIpContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpenMenu = onOpenMenu,
        onRefresh = viewModel::refresh,
        onAutoAllowChange = viewModel::setAutoAllow,
        onAdd = viewModel::add,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneIpContent(
    state: PhoneIpUiState,
    snackbarHostState: SnackbarHostState,
    onOpenMenu: () -> Unit,
    onRefresh: () -> Unit,
    onAutoAllowChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.phone_ip_title)) },
                navigationIcon = { MenuButton(onOpenMenu) },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            // Le premier chargement a son propre indicateur, au centre de l'écran.
            isRefreshing = state.isRefreshing && state.access != null,
            onRefresh = onRefresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val access = state.access
                val contentModifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                when {
                    access != null -> {
                        state.error?.let { Notice(stringResource(R.string.protection_stale_status, it.asString()), contentModifier) }
                        IpCard(access, contentModifier)
                        AutoAllowSwitch(state.autoAllow, onAutoAllowChange, contentModifier)
                        Button(
                            onClick = onAdd,
                            enabled = !state.isAdding,
                            modifier = contentModifier.height(52.dp),
                        ) {
                            if (state.isAdding) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                                Spacer(Modifier.size(12.dp))
                            }
                            Text(stringResource(R.string.action_add))
                        }
                    }

                    state.error != null && !state.isRefreshing -> LoadError(state.error, onRefresh, contentModifier)

                    else -> Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun IpCard(access: PhoneIpAccess, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.phone_ip_label), style = MaterialTheme.typography.titleMedium)
            // Sélectionnable : l'adresse peut être copiée.
            SelectionContainer {
                Text(access.ip, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            }
            Text(
                text = accessDescription(access),
                style = MaterialTheme.typography.bodyMedium,
                color = if (access.allowed) colors.primary else colors.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun accessDescription(access: PhoneIpAccess): String = when {
    access.allowed && access.rule != null -> stringResource(R.string.phone_ip_allowed_by_rule, access.rule)
    access.allowed -> stringResource(R.string.phone_ip_allowed)
    access.rule != null -> stringResource(R.string.phone_ip_blocked_by_rule, access.rule)
    else -> stringResource(R.string.phone_ip_blocked)
}

@Composable
private fun AutoAllowSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.phone_ip_auto_allow)) },
            supportingContent = { Text(stringResource(R.string.phone_ip_auto_allow_description)) },
            trailingContent = { Switch(checked = checked, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
        )
    }
}

@Composable
private fun Notice(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(text = text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LoadError(error: UiText, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.phone_ip_load_failed),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = error.asString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun PhoneIpPreview() {
    OnyxFilterTheme {
        PhoneIpContent(
            state = PhoneIpUiState(access = PhoneIpAccess("203.0.113.7", allowed = false, rule = null), autoAllow = true),
            snackbarHostState = remember { SnackbarHostState() },
            onOpenMenu = {},
            onRefresh = {},
            onAutoAllowChange = {},
            onAdd = {},
        )
    }
}
