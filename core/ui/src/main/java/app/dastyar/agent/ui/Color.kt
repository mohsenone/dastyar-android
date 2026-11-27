package app.dastyar.agent.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val Teal = Color(0xFF0C5C54)
internal val TealBright = Color(0xFF8ED9CC)
internal val Clay = Color(0xFF8E4E2A)
internal val Paper = Color(0xFFF4EFE6)
internal val Ink = Color(0xFF142824)
internal val Night = Color(0xFF0B100F)

internal val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9EDE6),
    onPrimaryContainer = Color(0xFF00211D),
    secondary = Clay,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4D4),
    onSecondaryContainer = Color(0xFF341100),
    tertiary = Color(0xFF6E5424),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E6C4),
    onTertiaryContainer = Color(0xFF241A00),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFBF7F1),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE6E0D4),
    onSurfaceVariant = Color(0xFF3C4945),
    surfaceTint = Teal,
    inverseSurface = Color(0xFF24302C),
    inverseOnSurface = Color(0xFFF3EEE6),
    inversePrimary = TealBright,
    surfaceDim = Color(0xFFD9D3C6),
    surfaceBright = Color(0xFFFFFCF7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2E9),
    surfaceContainer = Color(0xFFEFE9DE),
    surfaceContainerHigh = Color(0xFFE8E1D4),
    surfaceContainerHighest = Color(0xFFE0D8CA),
    outline = Color(0xFF6A7874),
    outlineVariant = Color(0xFFD0C8BA),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0xFF000000),
)

internal val DarkColors = darkColorScheme(
    primary = TealBright,
    onPrimary = Color(0xFF00332E),
    primaryContainer = Color(0xFF0E3E38),
    onPrimaryContainer = Color(0xFFC8EFE8),
    secondary = Color(0xFFF0B892),
    onSecondary = Color(0xFF4A220C),
    secondaryContainer = Color(0xFF5C321C),
    onSecondaryContainer = Color(0xFFFFE4D4),
    tertiary = Color(0xFFE4C98C),
    onTertiary = Color(0xFF3D2C00),
    tertiaryContainer = Color(0xFF56440F),
    onTertiaryContainer = Color(0xFFFFE8B4),
    background = Night,
    onBackground = Color(0xFFE6F0EC),
    surface = Color(0xFF101615),
    onSurface = Color(0xFFE6F0EC),
    surfaceVariant = Color(0xFF1C2825),
    onSurfaceVariant = Color(0xFFC5D4CE),
    surfaceTint = TealBright,
    inverseSurface = Color(0xFFE4EEEA),
    inverseOnSurface = Color(0xFF1A2421),
    inversePrimary = Teal,
    surfaceDim = Color(0xFF0B100F),
    surfaceBright = Color(0xFF2C3834),
    surfaceContainerLowest = Color(0xFF070A09),
    surfaceContainerLow = Color(0xFF121A18),
    surfaceContainer = Color(0xFF17211E),
    surfaceContainerHigh = Color(0xFF212C29),
    surfaceContainerHighest = Color(0xFF2B3834),
    outline = Color(0xFF8AA39C),
    outlineVariant = Color(0xFF2E3C38),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)
