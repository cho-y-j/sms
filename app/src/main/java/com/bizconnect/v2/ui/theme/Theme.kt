package com.bizconnect.v2.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Color Palette (Samsung One UI 6)
private val PrimaryBlue = Color(0xFF1E88E5)
private val SecondaryGreen = Color(0xFF43A047)
private val TertiaryOrange = Color(0xFFFFB74D)
private val ErrorRed = Color(0xFFE53935)
private val GrayLight = Color(0xFFFAFAFA)
private val GrayDark = Color(0xFF121212)
private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Black,
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFF81C784),
    onSecondary = Black,
    secondaryContainer = Color(0xFF2E7D32),
    onSecondaryContainer = Color(0xFFC8E6C9),
    tertiary = Color(0xFFFFD54F),
    onTertiary = Black,
    tertiaryContainer = Color(0xFFF57C00),
    onTertiaryContainer = Color(0xFFFFE0B2),
    error = Color(0xFFEF5350),
    onError = Black,
    errorContainer = Color(0xFFC62828),
    onErrorContainer = Color(0xFFFFCDD2),
    background = GrayDark,
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFFFFFFF),
    outline = Color(0xFF616161)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = SecondaryGreen,
    onSecondary = White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = TertiaryOrange,
    onTertiary = White,
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFFE65100),
    error = ErrorRed,
    onError = White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFF8B0000),
    background = GrayLight,
    onBackground = GrayDark,
    surface = White,
    onSurface = GrayDark,
    outline = Color(0xFF9E9E9E)
)

@Composable
fun BizConnectTheme(
    forceDarkMode: Boolean? = null,
    darkTheme: Boolean = forceDarkMode ?: isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BizConnectTypography,
        content = content
    )
}
