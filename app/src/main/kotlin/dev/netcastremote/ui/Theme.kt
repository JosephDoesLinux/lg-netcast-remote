package dev.netcastremote.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** The brand palette, from an indigo seed, for when wallpaper colors are off or unavailable. */
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

/**
 * Google Sans Flex, the Material 3 Expressive face, where the device ships it (Pixels do). Each weight
 * sets the variable font's wght axis. Devices without it fall back to the system sans.
 */
private val Flex = FontFamily((1..9).map { step -> Font(DeviceFontFamilyName("google-sans-flex"), FontWeight(step * 100)) })

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun Typography.withFamily(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
        displayLargeEmphasized = displayLargeEmphasized.f(), displayMediumEmphasized = displayMediumEmphasized.f(),
        displaySmallEmphasized = displaySmallEmphasized.f(), headlineLargeEmphasized = headlineLargeEmphasized.f(),
        headlineMediumEmphasized = headlineMediumEmphasized.f(), headlineSmallEmphasized = headlineSmallEmphasized.f(),
        titleLargeEmphasized = titleLargeEmphasized.f(), titleMediumEmphasized = titleMediumEmphasized.f(),
        titleSmallEmphasized = titleSmallEmphasized.f(), bodyLargeEmphasized = bodyLargeEmphasized.f(),
        bodyMediumEmphasized = bodyMediumEmphasized.f(), bodySmallEmphasized = bodySmallEmphasized.f(),
        labelLargeEmphasized = labelLargeEmphasized.f(), labelMediumEmphasized = labelMediumEmphasized.f(),
        labelSmallEmphasized = labelSmallEmphasized.f(),
    )
}

/**
 * Wallpaper dynamic color (Android 12+) or the brand palette, with expressive motion and type. Dark mode
 * uses true black so the remote is comfortable in a dark room. A TV frame seed tints only the primary
 * container, blended opaque so text on it keeps its contrast.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NetCastTheme(context: Context, seed: Int?, wallpaper: Boolean = true, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val base: ColorScheme = when {
        wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> BrandDark
        else -> BrandLight
    }
    val tinted = if (seed == null || seed == 0) {
        base
    } else {
        base.copy(
            primaryContainer = lerp(base.surfaceContainerHighest, Color(seed).copy(alpha = 1f), 0.4f),
            onPrimaryContainer = base.onSurface,
        )
    }
    val scheme = if (dark) tinted.copy(background = Color.Black, surface = Color.Black) else tinted
    val type = remember { Typography().withFamily(Flex) }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), typography = type, content = content)
}
