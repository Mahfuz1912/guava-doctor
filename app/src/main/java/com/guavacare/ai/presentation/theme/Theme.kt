package com.guavacare.ai.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.ThemeMode

private val GuavaLightColors = lightColorScheme(
    primary = OrchardGreenPrimary,
    onPrimary = OrchardOnPrimary,
    primaryContainer = OrchardPrimaryContainer,
    onPrimaryContainer = OrchardOnPrimaryContainer,
    secondary = OliveSecondary,
    onSecondary = OliveOnSecondary,
    secondaryContainer = OliveSecondaryContainer,
    onSecondaryContainer = OliveOnSecondaryContainer,
    tertiary = HarvestAmberTertiary,
    onTertiary = HarvestOnTertiary,
    tertiaryContainer = HarvestTertiaryContainer,
    onTertiaryContainer = HarvestOnTertiaryContainer,
    background = StoneSurface,
    onBackground = StoneOnSurface,
    surface = StoneSurface,
    onSurface = StoneOnSurface,
    surfaceVariant = StoneSurfaceContainerLow,
    onSurfaceVariant = StoneOnSurfaceVariant,
    surfaceContainerLow = StoneSurfaceContainerLow,
    surfaceContainer = StoneSurfaceContainer,
    surfaceContainerHigh = StoneSurfaceContainerHigh,
    outline = StoneOutline,
    outlineVariant = StoneOutlineVariant,
    error = AnthracnoseCrimson,
    errorContainer = AnthracnoseContainer,
    onErrorContainer = AnthracnoseOnContainer
)

private val GuavaDarkColors = darkColorScheme(
    primary = LeafSagePrimaryDark,
    onPrimary = LeafSageOnPrimaryDark,
    primaryContainer = LeafSagePrimaryContainerDark,
    onPrimaryContainer = LeafSageOnPrimaryContainerDark,
    secondary = OliveSecondaryDark,
    onSecondary = OliveOnSecondaryDark,
    secondaryContainer = OliveSecondaryContainerDark,
    onSecondaryContainer = OliveOnSecondaryContainerDark,
    tertiary = HarvestAmberDark,
    onTertiary = HarvestOnAmberDark,
    tertiaryContainer = HarvestAmberContainerDark,
    onTertiaryContainer = HarvestOnAmberContainerDark,
    background = CanopySurfaceDark,
    onBackground = CanopyOnSurfaceDark,
    surface = CanopySurfaceDark,
    onSurface = CanopyOnSurfaceDark,
    surfaceVariant = CanopySurfaceContainerLowDark,
    onSurfaceVariant = CanopyOnSurfaceVariantDark,
    surfaceContainerLow = CanopySurfaceContainerLowDark,
    surfaceContainer = CanopySurfaceContainerDark,
    surfaceContainerHigh = CanopySurfaceContainerHighDark,
    outline = CanopyOutlineDark,
    outlineVariant = CanopyOutlineVariantDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark
)

private val GuavaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun GuavaCareTheme(
    themeMode: ThemeMode,
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDark
                controller.isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    CompositionLocalProvider(LocalIsDarkTheme provides useDark) {
        MaterialTheme(
            colorScheme = if (useDark) GuavaDarkColors else GuavaLightColors,
            typography = buildGuavaCareTypography(language),
            shapes = GuavaShapes,
            content = content
        )
    }
}
