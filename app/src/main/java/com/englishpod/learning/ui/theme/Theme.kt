package com.englishpod.learning.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Indigo = Color(0xFF4338CA)
private val IndigoLight = Color(0xFFC7D2FE)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF1A1B22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B22),
    surfaceVariant = Color(0xFFE7E7F1),
    onSurfaceVariant = Color(0xFF474754),
    outline = Color(0xFF787885),
    outlineVariant = Color(0xFFC9C9D6),
    inverseSurface = Color(0xFF2E3038),
    inverseOnSurface = Color(0xFFF1F1F7),
    inversePrimary = IndigoLight,
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFFFCD34D),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF92400E),
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0E0F14),
    onBackground = Color(0xFFE4E4EF),
    surface = Color(0xFF15161C),
    onSurface = Color(0xFFE4E4EF),
    surfaceVariant = Color(0xFF2A2B35),
    onSurfaceVariant = Color(0xFFC7C7D4),
    outline = Color(0xFF8F8F9C),
    outlineVariant = Color(0xFF3A3B45),
    inverseSurface = Color(0xFFE4E4EF),
    inverseOnSurface = Color(0xFF2E3038),
    inversePrimary = Indigo,
    scrim = Color(0xFF000000),
)

@Composable
fun EnglishPodTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}
