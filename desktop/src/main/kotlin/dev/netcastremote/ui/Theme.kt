package dev.netcastremote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The brand palette from the indigo seed, the same one the Android app falls back to without wallpaper colors. */
private val BrandLight = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF8E4D34),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF380D00),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF293CA0),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC3C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFFFB59C),
    onTertiary = Color(0xFF55200A),
    tertiaryContainer = Color(0xFF71361F),
    onTertiaryContainer = Color(0xFFFFDBCF),
)

/** Brand colors with expressive motion; [theme] is "system", "light", or "dark". A TV frame seed tints the primary container. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NetCastTheme(theme: String, seed: Int?, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val base = if (dark) BrandDark else BrandLight
    val scheme = if (seed == null || seed == 0) base else base.copy(
        primaryContainer = lerp(base.surfaceContainerHighest, Color(seed).copy(alpha = 1f), 0.4f),
        onPrimaryContainer = base.onSurface,
    )
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), content = content)
}
