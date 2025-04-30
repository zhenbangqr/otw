package com.zhenbang.otw.ui.theme // Adjust package if needed

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- Use the new purple colors defined in Color.kt ---

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary, // Use your light purple
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary, // Adjust secondary if desired
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    // Define other colors (tertiary, error, background, surface etc.) as needed
    // For example:
    // background = Color(0xFFFFFBFE),
    // surface = Color(0xFFFFFBFE),
    // error = Color(0xFFB3261E),
    // onBackground = Color(0xFF1C1B1F),
    // onSurface = Color(0xFF1C1B1F),
    // onError = Color(0xFFFFFFFF),
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary, // Use your dark purple
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary, // Adjust secondary if desired
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    // Define other colors (tertiary, error, background, surface etc.) as needed
    // For example:
    // background = Color(0xFF1C1B1F),
    // surface = Color(0xFF1C1B1F),
    // error = Color(0xFFF2B8B5),
    // onBackground = Color(0xFFE6E1E5),
    // onSurface = Color(0xFFE6E1E5),
    // onError = Color(0xFF601410),
)

@Composable
fun OnTheWayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb() // Or use surface/background
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme // Adjust based on status bar color
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Assuming you have Typography defined
        content = content
    )
}