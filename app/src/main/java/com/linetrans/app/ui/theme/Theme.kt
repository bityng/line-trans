package com.linetrans.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.linetrans.app.data.SettingsRepository
import com.linetrans.app.model.ThemeMode

// —— 品牌色：靛蓝 + 青绿，兼顾浅色与深色的对比度 ——
private val BrandPrimary = Color(0xFF3D5AFE)
private val BrandSecondary = Color(0xFF00A8A0)
private val BrandTertiary = Color(0xFF7C4DFF)

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF001158),
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F2EE),
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = BrandTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9DDFF),
    onTertiaryContainer = Color(0xFF21005D),
    background = Color(0xFFF5F6FB),
    onBackground = Color(0xFF14161C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFE8EAF3),
    onSurfaceVariant = Color(0xFF454A57),
    outline = Color(0xFF9AA0AE),
    outlineVariant = Color(0xFFD8DCE8),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF2840A6),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFF7FD9D2),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF00504B),
    onSecondaryContainer = Color(0xFFC9F2EE),
    tertiary = Color(0xFFCFBCFF),
    onTertiary = Color(0xFF381E72),
    tertiaryContainer = Color(0xFF4F378B),
    onTertiaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFE3E4EA),
    surface = Color(0xFF161920),
    onSurface = Color(0xFFE3E4EA),
    surfaceVariant = Color(0xFF242833),
    onSurfaceVariant = Color(0xFFC3C7D4),
    outline = Color(0xFF8D92A0),
    outlineVariant = Color(0xFF3A3F4B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** 按用户设置缩放字号与行高。 */
private fun TextStyle.scaled(scale: Float): TextStyle {
    if (scale == 1f) return this
    val size = if (fontSize == TextUnit.Unspecified) 14.sp else fontSize * scale
    val line = if (lineHeight == TextUnit.Unspecified) size * 1.45f else lineHeight * scale
    return copy(fontSize = size, lineHeight = line)
}

private fun buildTypography(scale: Float): Typography {
    if (scale == 1f) return Typography()
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.scaled(scale),
        displayMedium = base.displayMedium.scaled(scale),
        displaySmall = base.displaySmall.scaled(scale),
        headlineLarge = base.headlineLarge.scaled(scale),
        headlineMedium = base.headlineMedium.scaled(scale),
        headlineSmall = base.headlineSmall.scaled(scale),
        titleLarge = base.titleLarge.scaled(scale),
        titleMedium = base.titleMedium.scaled(scale).copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.scaled(scale).copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.scaled(scale),
        bodyMedium = base.bodyMedium.scaled(scale),
        bodySmall = base.bodySmall.scaled(scale),
        labelLarge = base.labelLarge.scaled(scale),
        labelMedium = base.labelMedium.scaled(scale),
        labelSmall = base.labelSmall.scaled(scale)
    )
}

@Composable
fun LineTransTheme(
    systemDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settings = SettingsRepository.settings
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = buildTypography(settings.uiScale.coerceIn(0.8f, 1.5f)),
        shapes = AppShapes,
        content = content
    )
}
