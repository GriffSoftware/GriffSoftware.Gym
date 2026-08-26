package com.griffgym.presentation.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalGriffGymColors = staticCompositionLocalOf { GriffGymDarkColors }
private val LocalGriffGymTypography = staticCompositionLocalOf { GriffGymTypographyDefaults }
private val LocalGriffGymShapes = staticCompositionLocalOf { GriffGymShapes() }
private val LocalGriffGymDimens = staticCompositionLocalOf { GriffGymDimens() }

/**
 * Material 3 is the technical foundation — [Text], ripples and window insets all read from
 * it — but the visual language is entirely ours, exposed through [GriffGymTheme].
 *
 * The app is dark-only by design: it is used under gym lighting, at arm's length, between
 * sets, so it deliberately ignores the system light/dark setting — a light variant would be
 * a different product.
 */
@Composable
fun GriffGymTheme(content: @Composable () -> Unit) {
    val colors = GriffGymDarkColors
    val typography = GriffGymTypographyDefaults

    CompositionLocalProvider(
        LocalGriffGymColors provides colors,
        LocalGriffGymTypography provides typography,
        LocalGriffGymShapes provides GriffGymShapes(),
        LocalGriffGymDimens provides GriffGymDimens(),
        LocalTextStyle provides typography.body.copy(color = colors.textPrimary),
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = colors.primary,
                onPrimary = colors.onPrimary,
                background = colors.background,
                onBackground = colors.textPrimary,
                surface = colors.surface,
                onSurface = colors.textPrimary,
                surfaceVariant = colors.surfaceVariant,
                onSurfaceVariant = colors.textSecondary,
                outline = colors.outlineStrong,
                error = colors.error,
            ),
            content = content,
        )
    }
}

object GriffGymTheme {

    val colors: GriffGymColorScheme
        @Composable @ReadOnlyComposable get() = LocalGriffGymColors.current

    val typography: GriffGymTypography
        @Composable @ReadOnlyComposable get() = LocalGriffGymTypography.current

    val shapes: GriffGymShapes
        @Composable @ReadOnlyComposable get() = LocalGriffGymShapes.current

    val dimens: GriffGymDimens
        @Composable @ReadOnlyComposable get() = LocalGriffGymDimens.current
}
