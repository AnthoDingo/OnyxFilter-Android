package io.github.anthodingo.onyxfilter.ui.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.domain.ClientAccess
import io.github.anthodingo.onyxfilter.ui.MenuButton
import io.github.anthodingo.onyxfilter.ui.SwipeAction
import io.github.anthodingo.onyxfilter.ui.UiText
import io.github.anthodingo.onyxfilter.ui.asString
import io.github.anthodingo.onyxfilter.ui.theme.OnyxFilterTheme

@Composable
fun BlockedClientsScreen(viewModel: BlockedClientsViewModel, onOpenMenu: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) { viewModel.refresh() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message.asString(context)) }
    }

    BlockedClientsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpenMenu = onOpenMenu,
        onRefresh = viewModel::refresh,
        onUnblock = viewModel::unblock,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedClientsContent(
    state: BlockedClientsUiState,
    snackbarHostState: SnackbarHostState,
    onOpenMenu: () -> Unit,
    onRefresh: () -> Unit,
    onUnblock: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.blocked_clients_title)) },
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
            val access = state.access
            when {
                access != null -> BlockedClientsList(state, access, onUnblock)

                state.error != null && !state.isRefreshing -> LoadError(state.error, onRefresh)

                else -> Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun BlockedClientsList(state: BlockedClientsUiState, access: ClientAccess, onUnblock: (String) -> Unit) {
    val clients = state.blockedClients
    LazyColumn(Modifier.fillMaxSize()) {
        // Actualisation impossible : la liste affichée est celle de la dernière lecture réussie.
        state.error?.let { error ->
            item(key = "error") { Notice(stringResource(R.string.protection_stale_status, error.asString())) }
        }

        // Mode liste blanche : les clients refusés ne comptent pas, les débloquer d'ici n'aurait pas de sens
        // (le serveur ajouterait le client aux clients autorisés).
        if (access.isAllowlistMode) {
            item(key = "allowlist") { Notice(stringResource(R.string.blocked_clients_allowlist_mode)) }
        }

        item(key = "hint") {
            Text(
                text = stringResource(
                    when {
                        clients.isEmpty() -> R.string.blocked_clients_empty
                        access.isAllowlistMode -> R.string.blocked_clients_allowlist_hint
                        else -> R.string.blocked_clients_hint
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (clients.isEmpty()) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (clients.isEmpty()) 48.dp else 12.dp),
            )
        }

        items(clients, key = { it }) { client ->
            SwipeAction(
                icon = R.drawable.ic_shield_check,
                label = stringResource(R.string.action_unblock),
                background = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                rowColor = MaterialTheme.colorScheme.surface,
                onSwipe = { onUnblock(client) },
                enabled = !access.isAllowlistMode,
            ) {
                ListItem(
                    leadingContent = { Icon(painterResource(R.drawable.ic_block), contentDescription = null) },
                    headlineContent = { Text(client) },
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Notice(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(text = text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LoadError(error: UiText, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.blocked_clients_load_failed),
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

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun BlockedClientsPreview() {
    OnyxFilterTheme {
        BlockedClientsContent(
            state = BlockedClientsUiState(access = ClientAccess(listOf("203.0.113.7", "198.51.100.0/24"), isAllowlistMode = false)),
            snackbarHostState = remember { SnackbarHostState() },
            onOpenMenu = {},
            onRefresh = {},
            onUnblock = {},
        )
    }
}
