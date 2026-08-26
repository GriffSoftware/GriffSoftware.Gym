package com.griffgym.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Near-square corners throughout. Gym equipment has edges, not pills — and a 2 dp radius
 * keeps the surfaces from looking like Material's default cards.
 */
@Immutable
data class GriffGymShapes(
    val card: Shape = RoundedCornerShape(2.dp),
    val button: Shape = RoundedCornerShape(0.dp),
    val input: Shape = RoundedCornerShape(0.dp),
    val badge: Shape = RoundedCornerShape(1.dp),
)
