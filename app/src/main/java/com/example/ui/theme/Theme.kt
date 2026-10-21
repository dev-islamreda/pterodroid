package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val ExpressiveDarkColorScheme = darkColorScheme(
    primary = M3ExpDarkPrimary,
    onPrimary = M3ExpDarkOnPrimary,
    primaryContainer = M3ExpDarkPrimaryContainer,
    onPrimaryContainer = M3ExpDarkOnPrimaryContainer,
    secondary = M3ExpDarkSecondary,
    onSecondary = M3ExpDarkOnSecondary,
    secondaryContainer = M3ExpDarkSecondaryContainer,
    onSecondaryContainer = M3ExpDarkOnSecondaryContainer,
    tertiary = M3ExpDarkTertiary,
    onTertiary = M3ExpDarkOnTertiary,
    tertiaryContainer = M3ExpDarkTertiaryContainer,
    onTertiaryContainer = M3ExpDarkOnTertiaryContainer,
    background = M3ExpDarkBackground,
    onBackground = M3ExpDarkOnBackground,
    surface = M3ExpDarkSurface,
    onSurface = M3ExpDarkOnSurface,
    surfaceVariant = M3ExpDarkSurfaceVariant,
    onSurfaceVariant = M3ExpDarkOnSurfaceVariant,
    surfaceContainer = M3ExpDarkSurfaceContainer,
    surfaceContainerHigh = M3ExpDarkSurfaceContainerHigh,
    outline = M3ExpDarkOutline,
    outlineVariant = M3ExpDarkOutlineVariant,
    error = StatusOffline
)

private val ExpressiveLightColorScheme = lightColorScheme(
    primary = M3ExpLightPrimary,
    onPrimary = M3ExpLightOnPrimary,
    primaryContainer = M3ExpLightPrimaryContainer,
    onPrimaryContainer = M3ExpLightOnPrimaryContainer,
    secondary = M3ExpLightSecondary,
    onSecondary = M3ExpLightOnSecondary,
    secondaryContainer = M3ExpLightSecondaryContainer,
    onSecondaryContainer = M3ExpLightOnSecondaryContainer,
    tertiary = M3ExpLightTertiary,
    onTertiary = M3ExpLightOnTertiary,
    tertiaryContainer = M3ExpLightTertiaryContainer,
    onTertiaryContainer = M3ExpLightOnTertiaryContainer,
    background = M3ExpLightBackground,
    onBackground = M3ExpLightOnBackground,
    surface = M3ExpLightSurface,
    onSurface = M3ExpLightOnSurface,
    surfaceVariant = M3ExpLightSurfaceVariant,
    onSurfaceVariant = M3ExpLightOnSurfaceVariant,
    surfaceContainer = M3ExpLightSurfaceContainer,
    surfaceContainerHigh = M3ExpLightSurfaceContainerHigh,
    outline = M3ExpLightOutline,
    outlineVariant = M3ExpLightOutlineVariant,
    error = StatusOffline
)

enum class AppThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

@Composable
fun PterodroidTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ExpressiveDarkColorScheme
        else -> ExpressiveLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun PteroPilotTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = PterodroidTheme(themeMode = themeMode, dynamicColor = dynamicColor, content = content)
