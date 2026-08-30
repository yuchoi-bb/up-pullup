package com.pullup.tracker.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val Sky = Color(0xFF38BDF8)
val SkyDeep = Color(0xFF0284C7)
val Lime = Color(0xFFA3E635)
val Coral = Color(0xFFFB7185)
val Ink = Color(0xFF0B1120)
val InkSoft = Color(0xFF141E33)
val Cloud = Color(0xFFF5F7FB)

private val DarkColors = darkColorScheme(
    primary = Sky,
    onPrimary = Ink,
    primaryContainer = Color(0xFF0E3A56),
    onPrimaryContainer = Color(0xFFD6F1FF),
    secondary = Lime,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF2C3D14),
    onSecondaryContainer = Color(0xFFE4F7BF),
    tertiary = Coral,
    onTertiary = Ink,
    background = Ink,
    onBackground = Color(0xFFE6ECF5),
    surface = InkSoft,
    onSurface = Color(0xFFE6ECF5),
    surfaceVariant = Color(0xFF1D2A42),
    onSurfaceVariant = Color(0xFF9FB0C9),
    outline = Color(0xFF32405A),
    error = Color(0xFFFF8A8A),
    onError = Ink
)

private val LightColors = lightColorScheme(
    primary = SkyDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F1FF),
    onPrimaryContainer = Color(0xFF04263A),
    secondary = Color(0xFF4D7C0F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4F7BF),
    onSecondaryContainer = Color(0xFF1E2E05),
    tertiary = Color(0xFFE11D48),
    onTertiary = Color.White,
    background = Cloud,
    onBackground = Color(0xFF101827),
    surface = Color.White,
    onSurface = Color(0xFF101827),
    surfaceVariant = Color(0xFFE7EDF6),
    onSurfaceVariant = Color(0xFF4B5A70),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun UpPullupTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val context = LocalContext.current
    SideEffect {
        context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
