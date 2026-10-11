package io.github.anthodingo.onyxfilter.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Ligne qui déclenche [onSwipe] une fois glissée vers la gauche ou la droite, puis revient en place :
 * l'appelant décide de la suite (confirmation, retrait de la liste...).
 *
 * @param background couleur de fond révélée par le glissement, [contentColor] celle de l'icône.
 * @param rowColor fond opaque de la ligne, qui masque [background] au repos.
 */
@Composable
fun SwipeAction(
    @DrawableRes icon: Int,
    label: String,
    background: Color,
    contentColor: Color,
    rowColor: Color,
    onSwipe: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        gesturesEnabled = enabled,
        backgroundContent = {
            val alignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background)
                    .padding(horizontal = 16.dp),
                contentAlignment = alignment,
            ) {
                Icon(painterResource(icon), contentDescription = label, tint = contentColor)
            }
        },
        onDismiss = {
            onSwipe()
            scope.launch { state.reset() }
        },
    ) {
        Box(Modifier.background(rowColor)) { content() }
    }
}
