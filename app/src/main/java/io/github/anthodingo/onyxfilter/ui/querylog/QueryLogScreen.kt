package io.github.anthodingo.onyxfilter.ui.querylog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.domain.QueryLogEntry
import io.github.anthodingo.onyxfilter.domain.QueryLogReason
import io.github.anthodingo.onyxfilter.ui.MenuButton
import io.github.anthodingo.onyxfilter.ui.UiText
import io.github.anthodingo.onyxfilter.ui.asString
import io.github.anthodingo.onyxfilter.ui.formatShortDay
import io.github.anthodingo.onyxfilter.ui.formatTimeWithSeconds
import io.github.anthodingo.onyxfilter.ui.theme.OnyxFilterTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun QueryLogScreen(viewModel: QueryLogViewModel, onOpenMenu: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.onShown() }

    QueryLogContent(
        state = state,
        onOpenMenu = onOpenMenu,
        onRefresh = viewModel::refresh,
        onSearchChange = viewModel::onSearchChange,
        onLoadMore = viewModel::loadMore,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueryLogContent(
    state: QueryLogUiState,
    onOpenMenu: () -> Unit,
    onRefresh: () -> Unit,
    onSearchChange: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.query_log_title)) },
                navigationIcon = { MenuButton(onOpenMenu) },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SearchField(
                search = state.search,
                onSearchChange = onSearchChange,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            PullToRefreshBox(
                // Le premier chargement a son propre indicateur, au centre de l'écran.
                isRefreshing = state.isRefreshing && state.isLoaded,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isLoaded -> QueryLogList(state, onLoadMore)

                    state.error != null && !state.isRefreshing -> LoadError(state.error, onRefresh)

                    else -> Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(search: String, onSearchChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = search,
        onValueChange = onSearchChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.query_log_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = if (search.isNotEmpty()) {
            {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.query_log_clear_search))
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun QueryLogList(state: QueryLogUiState, onLoadMore: () -> Unit) {
    val listState = rememberLazyListState()
    LoadMoreWhenNearEnd(listState, onLoadMore)

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        // Actualisation impossible : les requêtes affichées sont celles de la dernière lecture réussie.
        state.error?.let { error ->
            item(key = "error") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.protection_stale_status, error.asString()),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (state.entries.isEmpty()) {
            item(key = "empty") {
                val search = state.search.trim()
                Text(
                    text = if (search.isEmpty()) {
                        stringResource(R.string.query_log_empty)
                    } else {
                        stringResource(R.string.query_log_no_match, search)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 48.dp),
                )
            }
        }

        itemsIndexed(state.entries) { index, entry ->
            if (index > 0) HorizontalDivider()
            QueryLogRow(entry)
        }

        when {
            state.isLoadingMore -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            state.loadMoreError != null -> item(key = "load_more_error") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.query_log_load_more_failed, state.loadMoreError.asString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onLoadMore) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }

    // Nouvelle recherche : retour en haut de la liste.
    LaunchedEffect(state.search) { if (state.entries.isNotEmpty()) listState.scrollToItem(0) }
}

// Lit la page suivante quand il ne reste plus que quelques requêtes à faire défiler.
@Composable
private fun LoadMoreWhenNearEnd(listState: LazyListState, onLoadMore: () -> Unit) {
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { nearEnd }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }
}

@Composable
private fun QueryLogRow(entry: QueryLogEntry) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        leadingContent = {
            Icon(
                painter = painterResource(if (entry.blocked) R.drawable.ic_shield_off else R.drawable.ic_shield_check),
                contentDescription = stringResource(if (entry.blocked) R.string.query_log_blocked else R.string.query_log_allowed),
                tint = if (entry.blocked) colors.error else colors.primary,
            )
        },
        headlineContent = {
            Text(entry.domain, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text(
                    text = listOfNotNull(entry.client, entry.clientDetail).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = outcome(entry),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (entry.blocked) colors.error else colors.onSurfaceVariant,
                )
            }
        },
        trailingContent = if (entry.time != null) {
            { EntryTime(entry.time) }
        } else {
            null
        },
    )
}

// "AAAA · Liste de filtres · ads.example · 3 ms" ou "A · Résolue · via 1.1.1.1 · 12 ms".
@Composable
private fun outcome(entry: QueryLogEntry): String {
    val detail = entry.reasonDetail ?: entry.upstream?.let { stringResource(R.string.query_log_upstream, it) }
    return listOfNotNull(
        entry.type.ifBlank { null },
        reasonLabel(entry),
        detail,
        stringResource(R.string.query_log_processing_time, entry.processingTimeMs),
    ).joinToString(" · ")
}

@Composable
private fun reasonLabel(entry: QueryLogEntry): String = stringResource(
    when (entry.reason) {
        QueryLogReason.Resolved -> R.string.query_log_reason_resolved
        QueryLogReason.Cached -> R.string.query_log_reason_cached
        QueryLogReason.Rewritten -> R.string.query_log_reason_rewritten
        QueryLogReason.SafeSearch -> R.string.query_log_reason_safe_search
        QueryLogReason.CustomRule -> R.string.query_log_reason_custom_rule
        QueryLogReason.Filtered -> R.string.query_log_reason_filtered
        QueryLogReason.BlockedService -> R.string.query_log_reason_blocked_service
        QueryLogReason.SecurityThreat -> R.string.query_log_reason_security_threat
        QueryLogReason.ParentalControl -> R.string.query_log_reason_parental_control
        QueryLogReason.Unknown -> if (entry.blocked) R.string.query_log_blocked else R.string.query_log_allowed
    },
)

// Heure de la requête, précédée du jour si ce n'est pas aujourd'hui.
@Composable
private fun EntryTime(time: Instant) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    Column(horizontalAlignment = Alignment.End) {
        if (time.atZone(zone).toLocalDate() != LocalDate.now(zone)) {
            Text(formatShortDay(context, time), style = MaterialTheme.typography.labelSmall)
        }
        Text(formatTimeWithSeconds(context, time), style = MaterialTheme.typography.labelMedium)
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
            text = stringResource(R.string.query_log_load_failed),
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

private const val LOAD_MORE_THRESHOLD = 10

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun QueryLogPreview() {
    val now = Instant.now()
    OnyxFilterTheme {
        QueryLogContent(
            state = QueryLogUiState(
                entries = listOf(
                    QueryLogEntry(now, "ads.example", "A", "192.168.1.20", null, QueryLogReason.Filtered, true, "EasyList", null, 1),
                    QueryLogEntry(now.minusSeconds(4), "example.com", "AAAA", "192.168.1.21", null, QueryLogReason.Resolved, false, null, "1.1.1.1", 12),
                    QueryLogEntry(now.minusSeconds(9), "onyxfilter.maison", "HTTPS", "203.0.113.7", "🇫🇷 FR · Orange", QueryLogReason.Cached, false, null, null, 0),
                ),
                hasMore = true,
                isLoaded = true,
            ),
            onOpenMenu = {},
            onRefresh = {},
            onSearchChange = {},
            onLoadMore = {},
        )
    }
}
