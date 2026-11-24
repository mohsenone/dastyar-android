package app.dastyar.agent.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF16332F)
private val Teal = Color(0xFF0E6B62)
private val TealBright = Color(0xFF8ED4C8)
private val Clay = Color(0xFF8C4A28)
private val Paper = Color(0xFFF6F3EC)
private val Night = Color(0xFF101412)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EFE8),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Clay,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCC),
    onSecondaryContainer = Color(0xFF341100),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E1D6),
    onSurfaceVariant = Color(0xFF3E4946),
    error = Color(0xFFB3261E),
    outline = Color(0xFF6F7A76),
)

private val DarkColors = darkColorScheme(
    primary = TealBright,
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF0E4F48),
    onPrimaryContainer = Color(0xFFC8EFE8),
    secondary = Color(0xFFFFB68D),
    onSecondary = Color(0xFF542205),
    secondaryContainer = Color(0xFF6D3820),
    background = Night,
    onBackground = Color(0xFFE7E2D8),
    surface = Night,
    onSurface = Color(0xFFE7E2D8),
    surfaceVariant = Color(0xFF2A3330),
    onSurfaceVariant = Color(0xFFC4CCC8),
    error = Color(0xFFFFB4AB),
    outline = Color(0xFF899490),
)

private val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private val DastyarType = Typography(
    headlineMedium = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun DastyarTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = DastyarType, content = content)
}
