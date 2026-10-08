package com.daylight.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF315C49), onPrimary = Color.White, primaryContainer = Color(0xFFE3EDE1), onPrimaryContainer = Color(0xFF244E3D),
    secondary = Color(0xFF8A633A), secondaryContainer = Color(0xFFF3E8D4), onSecondaryContainer = Color(0xFF624B2C),
    background = Color(0xFFF8F7F2), onBackground = Color(0xFF262E29), surface = Color(0xFFFFFEFA), onSurface = Color(0xFF262E29),
    surfaceVariant = Color(0xFFEEEFE6), onSurfaceVariant = Color(0xFF60695F), outline = Color(0xFF7B8479), outlineVariant = Color(0xFFDEE2D7),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFB2D0B5), onPrimary = Color(0xFF173726), primaryContainer = Color(0xFF2B493A), onPrimaryContainer = Color(0xFFDAECD7),
    secondary = Color(0xFFE2C291), secondaryContainer = Color(0xFF4D402E), onSecondaryContainer = Color(0xFFF6E4C7),
    background = Color(0xFF151B17), onBackground = Color(0xFFE8ECE4), surface = Color(0xFF1E2721), onSurface = Color(0xFFE8ECE4),
    surfaceVariant = Color(0xFF2C362E), onSurfaceVariant = Color(0xFFBBC5B8), outline = Color(0xFF8A9889), outlineVariant = Color(0xFF3B473D),
)
@Composable
fun DaylightTheme(mode: String, content: @Composable () -> Unit) {
    val dark = mode == "Dark" || mode == "System" && isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        view.context.activity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light,
        typography = Typography(
            displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 36.sp, lineHeight = 42.sp),
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 38.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 35.sp),
            titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp),
        ), content = content)
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.activity() else null
    else -> null
}
