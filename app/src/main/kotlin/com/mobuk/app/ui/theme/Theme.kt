package com.mobuk.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Mob's look: near-black ink, warm cream paper, and a hot orange accent.
val Ink = Color(0xFF111111)
val Cream = Color(0xFFF7F3EC)
val Paper = Color(0xFFFFFDF9)
val Orange = Color(0xFFFF5A1F)
val OrangeDark = Color(0xFFD9440F)
val Mustard = Color(0xFFF2B705)
val Mint = Color(0xFF9FE1CB)
val Stone = Color(0xFF8A8580)
val StoneLight = Color(0xFFE6E1D8)
val Night = Color(0xFF161514)
val NightSurface = Color(0xFF211F1D)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Cream,
    primaryContainer = StoneLight,
    onPrimaryContainer = Ink,
    secondary = Orange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE1D3),
    onSecondaryContainer = OrangeDark,
    tertiary = Mustard,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFFFFF0B8),
    onTertiaryContainer = Ink,
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = StoneLight,
    onSurfaceVariant = Color(0xFF4F4B46),
    outline = Stone,
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Cream,
    onPrimary = Ink,
    primaryContainer = Color(0xFF3A3733),
    onPrimaryContainer = Cream,
    secondary = Orange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF5A2A18),
    onSecondaryContainer = Color(0xFFFFD9C8),
    tertiary = Mustard,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFF4F4000),
    onTertiaryContainer = Color(0xFFFFE89A),
    background = Night,
    onBackground = Cream,
    surface = NightSurface,
    onSurface = Cream,
    surfaceVariant = Color(0xFF3A3733),
    onSurfaceVariant = Color(0xFFCFC9C0),
    outline = Color(0xFF8A8580),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

val MobTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
)

@Composable
fun MobTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = MobTypography, content = content)
}
