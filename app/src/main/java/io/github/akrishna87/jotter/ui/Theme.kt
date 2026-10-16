package io.github.akrishna87.jotter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.akrishna87.jotter.model.ThemeMode

private val Amber = Color(0xFFB06400)
private val AmberDark = Color(0xFFFFB84D)

private val LightColors = LightKit.let { k ->
    lightColorScheme(
        primary = Amber,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFEFD5),
        onPrimaryContainer = Color(0xFF5C3300),
        secondary = k.sub,
        onSecondary = Color.White,
        secondaryContainer = k.chip,
        onSecondaryContainer = k.text,
        tertiary = Color(0xFF0B8463),
        background = k.bg,
        onBackground = k.text,
        surface = k.bg,
        onSurface = k.text,
        surfaceVariant = k.chip,
        onSurfaceVariant = k.sub,
        surfaceContainerLowest = k.card,
        surfaceContainerLow = k.card,
        surfaceContainer = k.card,
        surfaceContainerHigh = k.card,
        surfaceContainerHighest = k.chip,
        outline = Color(0xFFCFC6B8),
        outlineVariant = k.line,
        error = Color(0xFFC62828),
        onError = Color.White,
        inverseSurface = Color(0xFF2A2621),
        inverseOnSurface = Color(0xFFF6F3EE),
        inversePrimary = AmberDark,
        scrim = Color(0xFF140C00),
    )
}

private val DarkColors = DarkKit.let { k ->
    darkColorScheme(
        primary = AmberDark,
        onPrimary = Color(0xFF3A2200),
        primaryContainer = Color(0xFF4A3112),
        onPrimaryContainer = Color(0xFFFFE2B8),
        secondary = k.sub,
        onSecondary = k.bg,
        secondaryContainer = k.chip,
        onSecondaryContainer = k.text,
        tertiary = Color(0xFF3DD6A6),
        background = k.bg,
        onBackground = k.text,
        surface = k.bg,
        onSurface = k.text,
        surfaceVariant = k.chip,
        onSurfaceVariant = k.sub,
        surfaceContainerLowest = k.card,
        surfaceContainerLow = k.card,
        surfaceContainer = k.card,
        surfaceContainerHigh = Color(0xFF26231F),
        surfaceContainerHighest = k.chip,
        outline = Color(0xFF5A5247),
        outlineVariant = k.line,
        error = Color(0xFFFF8A80),
        onError = Color(0xFF3A0905),
        inverseSurface = Color(0xFFF3EEE7),
        inverseOnSurface = Color(0xFF1F1B16),
        inversePrimary = Amber,
        scrim = Color.Black,
    )
}

private val AppTypography = kitTypography()

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun JotterTheme(dark: Boolean, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalKit provides if (dark) DarkKit else LightKit) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, shapes = KitShapes, content = content)
    }
}
