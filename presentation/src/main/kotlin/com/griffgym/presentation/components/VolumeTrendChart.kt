package com.griffgym.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.griffgym.presentation.theme.GriffGymTheme

@Immutable
data class VolumeBar(
    val label: String,
    val ratio: Float,
    val highlighted: Boolean,
    val hasData: Boolean,
)

/**
 * Tonnage across the last seven days.
 *
 * Solid blocks, no gradients, no axis furniture — the shape of the week is the whole
 * message. Today's bar is amber; the rest are charcoal.
 */
@Composable
fun VolumeTrendChart(
    bars: List<VolumeBar>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 110.dp,
) {
    val colors = GriffGymTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            bars.forEach { bar ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // A trained day always shows a sliver, so "trained lightly" never looks
                    // the same as "did not train".
                    val ratio = if (bar.hasData) bar.ratio.coerceIn(0.06f, 1f) else 0.02f
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(ratio)
                            .background(
                                when {
                                    bar.highlighted -> colors.primary
                                    bar.hasData -> colors.surfaceVariant
                                    else -> colors.outline
                                },
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    modifier = Modifier.weight(1f),
                    style = GriffGymTheme.typography.labelSmall,
                    color = if (bar.highlighted) colors.primary else colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
