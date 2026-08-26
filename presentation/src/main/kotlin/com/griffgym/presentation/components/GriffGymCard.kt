package com.griffgym.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * The single surface primitive of the app: flat charcoal, one hairline border, no shadow.
 *
 * Depth comes from tonal layering and strokes rather than elevation, which is what keeps
 * the UI reading as stamped metal instead of floating paper. [accentBar] paints the
 * four-pixel amber edge the design uses to mark the item that matters.
 */
@Composable
fun GriffGymCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = GriffGymTheme.dimens.cardPadding,
    accentBar: Color? = null,
    background: Color = GriffGymTheme.colors.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = GriffGymTheme.colors
    val shape = GriffGymTheme.shapes.card

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            // Painted rather than laid out: a sibling Box would need the row's height,
            // which a wrap-content card does not have until its content is measured.
            .then(
                if (accentBar != null) {
                    Modifier.drawBehind {
                        drawRect(
                            color = accentBar,
                            size = Size(AccentBarWidth.toPx(), size.height),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .border(GriffGymTheme.dimens.borderWidth, colors.outline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                start = if (accentBar != null) contentPadding + AccentBarWidth else contentPadding,
                top = contentPadding,
                end = contentPadding,
                bottom = contentPadding,
            ),
        content = content,
    )
}

private val AccentBarWidth = 4.dp

/** A card header: title on the left, optional action on the right, hairline underneath. */
@Composable
fun CardHeader(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = GriffGymTheme.colors.textPrimary,
    action: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = GriffGymTheme.typography.headline,
                color = titleColor,
            )
            action?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        HairLine()
    }
}

@Composable
fun HairLine(modifier: Modifier = Modifier, color: Color = GriffGymTheme.colors.outline) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}
