package io.github.anthodingo.onyxfilter.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.anthodingo.onyxfilter.R
import io.github.anthodingo.onyxfilter.data.ServerUrl
import io.github.anthodingo.onyxfilter.data.Session
import io.github.anthodingo.onyxfilter.ui.protection.ProtectionScreen
import io.github.anthodingo.onyxfilter.ui.protection.ProtectionViewModel
import io.github.anthodingo.onyxfilter.ui.querylog.QueryLogScreen
import io.github.anthodingo.onyxfilter.ui.querylog.QueryLogViewModel
import kotlinx.coroutines.launch

/** Pages accessibles depuis le menu de navigation. */
enum class MainPage(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    Protection(R.string.nav_protection, R.drawable.ic_shield_outline),
    QueryLog(R.string.nav_query_log, R.drawable.ic_list),
}

/** Pages de l'application une fois connecté, avec le menu de navigation sur la gauche. */
@Composable
fun MainScreen(session: Session) {
    var page by rememberSaveable { mutableStateOf(MainPage.Protection) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openMenu: () -> Unit = { scope.launch { drawerState.open() } }

    // Retour : ferme le menu, puis revient à la page Protection avant de quitter l'application.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && page != MainPage.Protection) { page = MainPage.Protection }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp),
                )
                Text(
                    text = ServerUrl.displayName(session.serverUrl),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 16.dp),
                )
                HorizontalDivider(Modifier.padding(horizontal = 28.dp))
                Column(
                    Modifier
                        .padding(top = 8.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    MainPage.entries.forEach { entry ->
                        NavigationDrawerItem(
                            label = { Text(stringResource(entry.label)) },
                            icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                            selected = page == entry,
                            onClick = {
                                page = entry
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                }
            }
        },
    ) {
        // Un ViewModel par instance : rien n'est repris d'un serveur à l'autre.
        when (page) {
            MainPage.Protection -> ProtectionScreen(
                session = session,
                viewModel = viewModel(key = "protection:${session.serverUrl}", factory = ProtectionViewModel.Factory),
                onOpenMenu = openMenu,
            )

            MainPage.QueryLog -> QueryLogScreen(
                viewModel = viewModel(key = "querylog:${session.serverUrl}", factory = QueryLogViewModel.Factory),
                onOpenMenu = openMenu,
            )
        }
    }
}
