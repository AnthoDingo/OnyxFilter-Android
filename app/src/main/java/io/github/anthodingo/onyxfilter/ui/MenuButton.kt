package io.github.anthodingo.onyxfilter.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.anthodingo.onyxfilter.R

/** Bouton de la barre supérieure qui ouvre le menu de navigation. */
@Composable
fun MenuButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.nav_open))
    }
}
