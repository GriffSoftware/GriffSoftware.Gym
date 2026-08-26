package com.griffgym.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A dashed rectangular stroke, used for the additive "+ ADD ..." affordances. */
fun Modifier.dashedBorder(
    color: Color,
    width: Dp = 2.dp,
    dash: Dp = 6.dp,
    gap: Dp = 5.dp,
): Modifier = drawBehind {
    val strokeWidth = width.toPx()
    drawRect(
        color = color,
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()), 0f),
        ),
    )
}
