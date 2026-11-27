package app.dastyar.agent.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

internal val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

internal val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private val ScriptLine = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun script(family: FontFamily, weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    lineHeightStyle = ScriptLine,
)

internal fun dastyarTypography(family: FontFamily) = Typography(
    displayLarge = script(family, FontWeight.Bold, 48, 56),
    displayMedium = script(family, FontWeight.Bold, 40, 48),
    displaySmall = script(family, FontWeight.Bold, 32, 40),
    headlineLarge = script(family, FontWeight.Bold, 30, 38),
    headlineMedium = script(family, FontWeight.Bold, 26, 34),
    headlineSmall = script(family, FontWeight.Bold, 22, 30),
    titleLarge = script(family, FontWeight.Medium, 20, 28),
    titleMedium = script(family, FontWeight.Medium, 16, 24),
    titleSmall = script(family, FontWeight.Medium, 14, 20),
    bodyLarge = script(family, FontWeight.Normal, 16, 26),
    bodyMedium = script(family, FontWeight.Normal, 14, 22),
    bodySmall = script(family, FontWeight.Normal, 12, 18),
    labelLarge = script(family, FontWeight.Medium, 14, 20),
    labelMedium = script(family, FontWeight.Medium, 12, 16),
    labelSmall = script(family, FontWeight.Medium, 11, 16),
)
