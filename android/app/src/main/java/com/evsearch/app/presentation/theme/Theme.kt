package com.evsearch.app.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Official Samsung One UI 9.0 (Galaxy AI Era) Dark Palette
 * - 더 깊은 AMOLED 블랙 배경과 부드러운 컨테이너 톤
 * - One UI 9 스타일의 채도 높은 틸/블루 액센트
 */
val SamsungOneUIDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00C896),         // One UI 9 Eco Teal (EV/Energy accent)
    onPrimary = Color(0xFF0B0D14),
    primaryContainer = Color(0xFF06402F),
    onPrimaryContainer = Color(0xFF9FF0D8),
    secondary = Color(0xFF3B82F6),       // One UI 9 Intelligence Blue
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFBFDBFE),
    tertiary = Color(0xFF818CF8),
    background = Color(0xFF0B0D14),      // One UI 9 True AMOLED Black Background
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF161A26),         // One UI 9 Elevated Card Surface
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1F2433),  // One UI 9 Pill / Chip Surface
    onSurfaceVariant = Color(0xFFA8B3C7),
    surfaceContainerHigh = Color(0xFF1A1F2E),
    outline = Color(0xFF2A3042),         // One UI 9 Hairline Border
    outlineVariant = Color(0xFF232838),
    error = Color(0xFFFF4757)
)

val SamsungOneUILightColorScheme = lightColorScheme(
    primary = Color(0xFF00A87D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFF7F0),
    onPrimaryContainer = Color(0xFF005C44),
    secondary = Color(0xFF2563EB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    background = Color(0xFFF3F4F6),      // One UI 9 Light Gray Background
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE9EDF3),
    onSurfaceVariant = Color(0xFF4B5563),
    surfaceContainerHigh = Color(0xFFF8FAFC),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFEEF2F7),
    error = Color(0xFFEF4444)
)

@Composable
fun EVSearchTheme(
    darkTheme: Boolean = true, // Samsung One UI Dark Mode Default
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SamsungOneUIDarkColorScheme
        else -> SamsungOneUILightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
