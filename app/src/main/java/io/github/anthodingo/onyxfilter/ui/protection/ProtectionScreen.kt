package io.github.anthodingo.onyxfilter.ui.protection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.data.ServerUrl
import io.github.anthodingo.onyxfilter.data.Session
import io.github.anthodingo.onyxfilter.domain.DisableDuration
import io.github.anthodingo.onyxfilter.domain.DnsStats
import io.github.anthodingo.onyxfilter.domain.DurationParts
import io.github.anthodingo.onyxfilter.domain.ProtectionStatus
import io.github.anthodingo.onyxfilter.domain.StatsFormat
import io.github.anthodingo.onyxfilter.ui.UiText
import io.github.anthodingo.onyxfilter.ui.asString
import io.github.anthodingo.onyxfilter.ui.formatReEnableTime
import io.github.anthodingo.onyxfilter.ui.theme.OnyxFilterTheme
import io.github.anthodingo.onyxfilter.update.Obtainium
import io.github.anthodingo.onyxfilter.update.installerPackageName
import io.github.anthodingo.onyxfilter.update.openObtainium
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun ProtectionScreen(session: Session, viewModel: ProtectionViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showCustomDuration by rememberSaveable { mutableStateOf(false) }

    LifecycleStartEffect(viewModel) {
        viewModel.startPolling()
        onStopOrDispose { viewModel.stopPolling() }
    }

    LaunchedEffect(viewModel) {
        viewModel.actionErrors.collect { message -> snackbarHostState.showSnackbar(message.asString(context)) }
    }

    val stats by viewModel.stats.collectAsStateWithLifecycle()
    // Inutile une fois l'application installée par Obtainium, qui la suit déjà.
    val offerObtainium = remember { !Obtainium.isInstaller(context.installerPackageName()) }

    ProtectionContent(
        state = state,
        stats = stats,
        now = rememberNow(ticking = state.status?.isDisabledTemporarily == true),
        serverLabel = ServerUrl.displayName(session.serverUrl),
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onEnable = viewModel::enable,
        onDisable = viewModel::disable,
        onCustomDuration = { showCustomDuration = true },
        onObtainium = if (offerObtainium) context::openObtainium else null,
        onLogout = viewModel::logout,
    )

    if (showCustomDuration) {
        CustomDurationDialog(
            onConfirm = { duration ->
                showCustomDuration = false
                viewModel.disable(duration)
            },
            onDismiss = { showCustomDuration = false },
        )
    }
}

// Heure courante, mise à jour chaque seconde pendant une désactivation temporaire (compte à rebours).
@Composable
private fun rememberNow(ticking: Boolean): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(ticking) {
        now = Instant.now()
        while (ticking) {
            delay(1_000 - now.toEpochMilli() % 1_000)
            now = Instant.now()
        }
    }
    return now
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtectionContent(
    state: ProtectionUiState,
    stats: DnsStats?,
    now: Instant,
    serverLabel: String,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onEnable: () -> Unit,
    onDisable: (DisableDuration) -> Unit,
    onCustomDuration: () -> Unit,
    /** Ajout à Obtainium pour les mises à jour ; `null` masque l'entrée du menu. */
    onObtainium: (() -> Unit)?,
    onLogout: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(
                            text = serverLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.action_refresh))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (onObtainium != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_obtainium)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_system_update), contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onObtainium()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_logout)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_logout), contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onLogout()
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            // Le premier chargement a son propre indicateur, au centre de l'écran.
            isRefreshing = state.isRefreshing && state.status != null,
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
                val status = state.status
                val contentModifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                when {
                    status != null -> {
                        state.error?.let { StaleStatusBanner(it, contentModifier) }
                        StatusCard(status, now, contentModifier)
                        PrimaryAction(status, state.isUpdating, onEnable, onDisable, contentModifier)
                        DisableOptions(status, state.isUpdating, onDisable, onCustomDuration, contentModifier)
                        stats?.let { StatsCard(it, contentModifier) }
                        Text(
                            text = stringResource(R.string.protection_restart_note),
                            modifier = contentModifier,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
private fun StatusCard(status: ProtectionStatus, now: Instant, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (status.enabled) colors.primaryContainer else colors.errorContainer
    val contentColor = if (status.enabled) colors.onPrimaryContainer else colors.onErrorContainer

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(if (status.enabled) R.drawable.ic_shield_check else R.drawable.ic_shield_off),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = contentColor,
            )
            Text(
                text = stringResource(if (status.enabled) R.string.protection_enabled else R.string.protection_disabled),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = statusDescription(status, now),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun statusDescription(status: ProtectionStatus, now: Instant): String {
    val until = status.disabledUntil
    return when {
        status.enabled -> stringResource(R.string.protection_enabled_description)
        until == null -> stringResource(R.string.protection_disabled_indefinitely_description)
        else -> {
            val remaining = status.remainingSeconds(now)
            if (remaining == 0L) {
                stringResource(R.string.protection_re_enabling)
            } else {
                stringResource(R.string.protection_disabled_until_description, reEnableTime(until, now), formatRemaining(remaining))
            }
        }
    }
}

// Relu à chaque recomposition : suit le compte à rebours et le format 12 h/24 h du téléphone.
@Composable
private fun reEnableTime(until: Instant, now: Instant): String = formatReEnableTime(LocalContext.current, until, now)

@Composable
private fun formatRemaining(seconds: Long): String {
    val parts = DurationParts.of(seconds)
    return when {
        parts.days > 0 -> stringResource(R.string.duration_days_hours, parts.days, parts.hours)
        parts.hours > 0 -> stringResource(R.string.duration_hours_minutes, parts.hours, parts.minutes)
        parts.minutes > 0 -> stringResource(R.string.duration_minutes_seconds, parts.minutes, parts.seconds)
        else -> stringResource(R.string.duration_seconds, parts.seconds)
    }
}

@Composable
private fun PrimaryAction(
    status: ProtectionStatus,
    isUpdating: Boolean,
    onEnable: () -> Unit,
    onDisable: (DisableDuration) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = if (status.enabled) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    } else {
        ButtonDefaults.buttonColors()
    }

    Button(
        onClick = { if (status.enabled) onDisable(DisableDuration.Indefinitely) else onEnable() },
        enabled = !isUpdating,
        colors = colors,
        modifier = modifier.height(52.dp),
    ) {
        if (isUpdating) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            Spacer(Modifier.size(12.dp))
        }
        Text(stringResource(if (status.enabled) R.string.action_disable else R.string.action_enable))
    }
}

@Composable
private fun DisableOptions(
    status: ProtectionStatus,
    isUpdating: Boolean,
    onDisable: (DisableDuration) -> Unit,
    onCustomDuration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Text(
            text = stringResource(if (status.enabled) R.string.disable_options_title else R.string.disable_options_title_disabled),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )

        val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
        DisableDuration.Presets.forEach { duration ->
            ListItem(
                headlineContent = { Text(durationLabel(duration)) },
                colors = itemColors,
                modifier = Modifier.clickable(enabled = !isUpdating) { onDisable(duration) },
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.disable_custom)) },
            colors = itemColors,
            modifier = Modifier.clickable(enabled = !isUpdating, onClick = onCustomDuration),
        )
        if (status.isDisabledTemporarily) {
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.disable_indefinitely)) },
                supportingContent = { Text(stringResource(R.string.disable_indefinitely_description)) },
                colors = itemColors,
                modifier = Modifier.clickable(enabled = !isUpdating) { onDisable(DisableDuration.Indefinitely) },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun durationLabel(duration: DisableDuration): String = when (duration) {
    DisableDuration.Indefinitely -> stringResource(R.string.disable_indefinitely)
    DisableDuration.UntilTomorrow -> stringResource(R.string.disable_until_tomorrow)
    is DisableDuration.Fixed -> {
        val seconds = duration.seconds
        when {
            seconds % 3_600 == 0L -> (seconds / 3_600).let { pluralStringResource(R.plurals.disable_for_hours, it.toInt(), it) }
            seconds % 60 == 0L -> (seconds / 60).let { pluralStringResource(R.plurals.disable_for_minutes, it.toInt(), it) }
            else -> pluralStringResource(R.plurals.disable_for_seconds, seconds.toInt(), seconds)
        }
    }
}

@Composable
private fun StaleStatusBanner(error: UiText, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
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

// Chiffres clés des dernières 24 heures : trois valeurs, pas de graphique (voir les widgets pour
// l'activité heure par heure).
@Composable
private fun StatsCard(stats: DnsStats, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.stats_card_title), style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth()) {
                StatValue(StatsFormat.count(stats.totalQueries, locale), stringResource(R.string.stats_label_queries), Modifier.weight(1f))
                StatValue(StatsFormat.count(stats.blockedQueries, locale), stringResource(R.string.stats_label_blocked), Modifier.weight(1f))
                StatValue(StatsFormat.percent(stats.blockedRatio, locale), stringResource(R.string.stats_label_ratio), Modifier.weight(1f))
            }
            stats.topBlockedDomain?.let { top ->
                Text(
                    text = stringResource(R.string.stats_top_blocked, top.name, StatsFormat.count(top.count, locale)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatValue(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun LoadError(error: UiText, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shield_off),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.protection_load_failed),
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

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ProtectionEnabledPreview() {
    OnyxFilterTheme {
        ProtectionContent(
            state = ProtectionUiState(status = ProtectionStatus(enabled = true, disabledUntil = null), isRefreshing = false),
            stats = PreviewStats,
            now = Instant.now(),
            serverLabel = "onyxfilter.maison:7037",
            snackbarHostState = remember { SnackbarHostState() },
            onRefresh = {},
            onEnable = {},
            onDisable = {},
            onCustomDuration = {},
            onObtainium = {},
            onLogout = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ProtectionDisabledPreview() {
    val now = Instant.now()
    OnyxFilterTheme(darkTheme = true) {
        ProtectionContent(
            state = ProtectionUiState(
                status = ProtectionStatus(enabled = false, disabledUntil = now.plusSeconds(552)),
                isRefreshing = false,
                error = UiText.Resource(R.string.error_timeout),
            ),
            stats = null,
            now = now,
            serverLabel = "onyxfilter.maison:7037",
            snackbarHostState = remember { SnackbarHostState() },
            onRefresh = {},
            onEnable = {},
            onDisable = {},
            onCustomDuration = {},
            onObtainium = {},
            onLogout = {},
        )
    }
}

private val PreviewStats = DnsStats(
    totalQueries = 12_345,
    blockedQueries = 321,
    blockedRatio = 0.026,
    averageProcessingTimeMs = 4,
    topBlockedDomain = DnsStats.RankedItem("ads.example", 87),
    hourlyQueries = List(24) { (it * 37L) % 500 },
    lastHourStart = null,
)
