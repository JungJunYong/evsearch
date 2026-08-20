package com.evsearch.app.presentation.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Apple 계열 dark-tile 팔레트 (docs: presentation/theme/AppleTokens.kt).
 * - primary 는 유일한 인터랙션 색(Sky Link Blue). 두 번째 브랜드색은 없다.
 * - surface 3단(#272729/#2a2a2c/#252527)은 인접 타일 분리를 위한 미세 단계차.
 * - outline 은 1px 헤어라인 전용. 그림자는 어디에도 쓰지 않는다.
 */
val EvAppleDarkColorScheme = darkColorScheme(
    primary = Apple.C.Accent,
    onPrimary = Color.White,
    primaryContainer = Apple.C.Tile2,
    onPrimaryContainer = Apple.C.Accent,
    secondary = Apple.C.Accent,
    onSecondary = Color.White,
    secondaryContainer = Apple.C.Tile2,
    onSecondaryContainer = Apple.C.TextMuted,
    tertiary = Apple.C.Accent,
    background = Apple.C.Canvas,
    onBackground = Apple.C.Text,
    surface = Apple.C.Tile1,
    onSurface = Apple.C.Text,
    surfaceVariant = Apple.C.Tile2,
    onSurfaceVariant = Apple.C.TextMuted,
    surfaceContainerHigh = Apple.C.Tile3,
    outline = Apple.C.Hairline,
    outlineVariant = Apple.C.Hairline,
    error = Apple.C.StatusSuspended
)

@Composable
fun EVSearchTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // dark-tile 표현 단일 사용: 다이내믹 컬러는 단일 액센트 원칙과 충돌해 쓰지 않는다.
    val colorScheme = EvAppleDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Apple.C.Black.toArgb()
            window.navigationBarColor = Apple.C.Black.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(
            bodyLarge = Apple.T.Body,
            bodyMedium = Apple.T.Caption,
            titleLarge = Apple.T.DisplayLg,
            titleMedium = Apple.T.DisplayMd,
            labelLarge = Apple.T.CaptionStrong,
            labelSmall = Apple.T.NavLink
        ),
        content = content
    )
}
