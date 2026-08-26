package com.griffgym.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The Griff Gym palette: warm charcoal under a single high-visibility amber accent.
 *
 * Amber is a signal, not a surface — it marks the call to action, the active tab, the top
 * set and a record, and nothing else. Everything structural is charcoal and bone.
 */
@Immutable
data class GriffGymColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceLowest: Color,
    val primary: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val outline: Color,
    val outlineStrong: Color,
    val squat: Color,
    val deadlift: Color,
    val bench: Color,
    val error: Color,
    val success: Color,
)

internal val GriffGymDarkColors = GriffGymColorScheme(
    background = Color(0xFF14120C),
    surface = Color(0xFF2A2721),
    surfaceVariant = Color(0xFF393527),
    surfaceLowest = Color(0xFF0D0C08),
    primary = Color(0xFFFFD000),
    onPrimary = Color(0xFF14120C),
    textPrimary = Color(0xFFEAE2CF),
    textSecondary = Color(0xFFD1C6AB),
    textTertiary = Color(0xFF999077),
    outline = Color(0xFF3A3529),
    outlineStrong = Color(0xFF4D4632),
    squat = Color(0xFFFFD000),
    deadlift = Color(0xFFFFB4AB),
    bench = Color(0xFF00EAFC),
    error = Color(0xFFFFB4AB),
    success = Color(0xFFB6D77A),
)
