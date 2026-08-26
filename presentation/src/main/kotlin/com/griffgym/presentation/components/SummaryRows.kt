package com.griffgym.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * "Przysiad [SILA] ......... 3x3x150kg" — the one-line exercise preview on the Home hero
 * card.
 */
@Composable
fun TrainingSummaryRow(
    name: String,
    badge: String?,
    scheme: String,
    modifier: Modifier = Modifier,
    badgeFilled: Boolean = false,
) {
    val colors = GriffGymTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = GriffGymTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                GriffGymBadge(
                    text = badge,
                    filled = badgeFilled,
                    color = if (badgeFilled) colors.primary else colors.textTertiary,
                )
            }
        }
        Text(
            text = scheme,
            style = GriffGymTheme.typography.data,
            color = colors.textSecondary,
        )
    }
}

/**
 * "SQ .................. 210kg" with a left edge marker. Tapping opens the editor —
 * reference maxes are the lifter's own numbers and must stay editable.
 */
@Composable
fun ReferenceMaxRow(
    code: String,
    weight: String,
    modifier: Modifier = Modifier,
    accent: Color = GriffGymTheme.colors.outlineStrong,
    onClick: (() -> Unit)? = null,
) {
    val colors = GriffGymTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.surfaceLowest)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = code,
                style = GriffGymTheme.typography.label,
                color = colors.textSecondary,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = weight,
                    style = GriffGymTheme.typography.dataLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = "kg",
                    style = GriffGymTheme.typography.dataSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
                )
            }
        }
    }
}

/** A labelled metric used in session summaries: VOLUME / DURATION / SETS. */
@Composable
fun MetricColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = GriffGymTheme.colors.textPrimary,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = GriffGymTheme.typography.labelSmall,
            color = GriffGymTheme.colors.textTertiary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = GriffGymTheme.typography.data,
            color = valueColor,
        )
    }
}
