package io.github.anthodingo.onyxfilter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Vert de l'interface web OnyxFilter (#22c55e) et ses déclinaisons.
private val LightColors = lightColorScheme(
    primary = Color(0xFF15803D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBBF7D0),
    onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFFBBF7D0),
    secondary = Color(0xFFCBD5E1),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0B1120),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFCBD5E1),
    surfaceContainerLowest = Color(0xFF070B16),
    surfaceContainerLow = Color(0xFF0F172A),
    surfaceContainer = Color(0xFF131C2E),
    surfaceContainerHigh = Color(0xFF1A2438),
    surfaceContainerHighest = Color(0xFF222D42),
)

@Composable
fun OnyxFilterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
