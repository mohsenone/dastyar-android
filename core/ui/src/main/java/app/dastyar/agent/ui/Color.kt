package app.dastyar.agent.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val Teal = Color(0xFF0E6B62)
internal val TealBright = Color(0xFF8ED4C8)
internal val Clay = Color(0xFF8C4A28)
internal val Paper = Color(0xFFF6F3EC)
internal val Ink = Color(0xFF16332F)
internal val Night = Color(0xFF101412)

internal val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7E8DF),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Clay,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCC),
    onSecondaryContainer = Color(0xFF341100),
    tertiary = Color(0xFF6E5A2E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E4C0),
    onTertiaryContainer = Color(0xFF241A00),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E1D6),
    onSurfaceVariant = Color(0xFF3E4946),
    surfaceTint = Teal,
    inverseSurface = Color(0xFF2A3330),
    inverseOnSurface = Color(0xFFF1EEE6),
    inversePrimary = TealBright,
    surfaceDim = Color(0xFFD7D2C7),
    surfaceBright = Color(0xFFFBF8F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1EEE6),
    surfaceContainer = Color(0xFFEBE7DE),
    surfaceContainerHigh = Color(0xFFE5E0D6),
    surfaceContainerHighest = Color(0xFFDFD9CE),
    outline = Color(0xFF6F7A76),
    outlineVariant = Color(0xFFBEC9C4),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0xFF000000),
)

internal val DarkColors = darkColorScheme(
    primary = TealBright,
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF0E4F48),
    onPrimaryContainer = Color(0xFFC8EFE8),
    secondary = Color(0xFFFFB68D),
    onSecondary = Color(0xFF542205),
    secondaryContainer = Color(0xFF6D3820),
    onSecondaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFFE6C98A),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5C4308),
    onTertiaryContainer = Color(0xFFFFE8B0),
    background = Night,
    onBackground = Color(0xFFE7E2D8),
    surface = Night,
    onSurface = Color(0xFFE7E2D8),
    surfaceVariant = Color(0xFF2A3330),
    onSurfaceVariant = Color(0xFFC4CCC8),
    surfaceTint = TealBright,
    inverseSurface = Color(0xFFE7E2D8),
    inverseOnSurface = Color(0xFF2A3330),
    inversePrimary = Teal,
    surfaceDim = Color(0xFF101412),
    surfaceBright = Color(0xFF363B38),
    surfaceContainerLowest = Color(0xFF0B0E0D),
    surfaceContainerLow = Color(0xFF191D1B),
    surfaceContainer = Color(0xFF1D2220),
    surfaceContainerHigh = Color(0xFF272C2A),
    surfaceContainerHighest = Color(0xFF323734),
    outline = Color(0xFF899490),
    outlineVariant = Color(0xFF3E4946),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)
