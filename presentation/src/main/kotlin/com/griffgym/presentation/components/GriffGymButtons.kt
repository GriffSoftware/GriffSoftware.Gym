package com.griffgym.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.griffgym.presentation.theme.GriffGymTheme

private val ShadowDepth = 3.dp

/**
 * The primary call to action: solid amber over a hard black offset that collapses when
 * pressed, so the button feels physically stamped rather than animated.
 */
@Composable
fun GriffGymPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentPaddingHorizontal: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val colors = GriffGymTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press = if (pressed) ShadowDepth else 0.dp

    // The shadow is drawn by the button itself rather than by a sibling Box, so it always
    // matches the real width - whether the button wraps its label or fills the screen.
    Row(
        modifier = modifier
            .padding(end = ShadowDepth, bottom = ShadowDepth)
            .offset(x = press, y = press)
            .drawBehind {
                val depth = (ShadowDepth - press).toPx()
                if (depth > 0f) {
                    drawRect(color = Color.Black, topLeft = Offset(depth, depth), size = size)
                }
            }
            .clip(GriffGymTheme.shapes.button)
            .background(if (enabled) colors.primary else colors.surfaceVariant)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = GriffGymTheme.dimens.touchTarget)
            .padding(horizontal = contentPaddingHorizontal, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentColor = if (enabled) colors.onPrimary else colors.textTertiary
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp),
            )
        }
        Text(text = text, style = GriffGymTheme.typography.label, color = contentColor)
    }
}

/** Transparent with a bone-coloured stroke — for secondary, reversible actions. */
@Composable
fun GriffGymSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = GriffGymTheme.colors.textSecondary,
    enabled: Boolean = true,
) {
    val shape = GriffGymTheme.shapes.button
    Row(
        modifier = modifier
            .clip(shape)
            .border(GriffGymTheme.dimens.borderWidthStrong, color, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = GriffGymTheme.dimens.touchTarget)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = GriffGymTheme.typography.label, color = color)
    }
}

/** Dashed outline for additive actions: "+ ADD EXERCISE", "+ ADD SET". */
@Composable
fun GriffGymDashedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = GriffGymTheme.colors.primary,
    textStyle: androidx.compose.ui.text.TextStyle = GriffGymTheme.typography.title,
) {
    val shape = GriffGymTheme.shapes.button
    Row(
        modifier = modifier
            .clip(shape)
            .dashedBorder(GriffGymTheme.colors.outlineStrong)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = GriffGymTheme.dimens.touchTarget)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(20.dp),
            )
        }
        Text(text = text, style = textStyle, color = color)
    }
}
