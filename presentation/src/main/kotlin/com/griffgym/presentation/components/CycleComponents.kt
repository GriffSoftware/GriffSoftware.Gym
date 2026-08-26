package com.griffgym.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.griffgym.domain.model.StrengthBlockTemplate
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * "DELOAD · 50%".
 *
 * The percentage comes from the block itself rather than being typed in here, so the badge
 * cannot drift away from what the deload week actually prescribes.
 */
val DeloadBadgeLabel: String =
    "DELOAD · ${StrengthBlockTemplate.DELOAD_PERCENT.toInt()}%"

/** Where one week of a cycle stands relative to the lifter. */
enum class CycleWeekState { COMPLETED, CURRENT, UPCOMING }

@Immutable
data class CycleWeekUiModel(
    val weekNumber: Int,
    val label: String,
    val isDeload: Boolean,
    val state: CycleWeekState,
)

/**
 * The six week bar across the top of a cycle card.
 *
 * Only one segment is amber — the week the lifter is actually in. Finished weeks are marked
 * with a tick in bone, weeks still ahead are charcoal. Same signalling rule as the rest of
 * the app: amber means "this is the one that matters right now".
 */
@Composable
fun CycleWeekTimeline(
    weeks: List<CycleWeekUiModel>,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        weeks.forEach { week ->
            val barColor = when (week.state) {
                CycleWeekState.CURRENT -> colors.primary
                CycleWeekState.COMPLETED -> colors.textSecondary
                CycleWeekState.UPCOMING -> colors.surfaceVariant
            }
            val labelColor = when (week.state) {
                CycleWeekState.CURRENT -> colors.primary
                CycleWeekState.COMPLETED -> colors.textSecondary
                CycleWeekState.UPCOMING -> colors.textTertiary
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (week.state == CycleWeekState.CURRENT) 6.dp else 4.dp)
                        .background(barColor),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "W${week.weekNumber}",
                    style = GriffGymTheme.typography.labelSmall,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(3.dp))
                when {
                    // The deload week is called out even before the lifter reaches it, so the
                    // shape of the block is readable at a glance.
                    week.isDeload -> Text(
                        text = "DELOAD",
                        style = GriffGymTheme.typography.labelSmall,
                        color = if (week.state == CycleWeekState.UPCOMING) {
                            colors.textTertiary
                        } else {
                            colors.bench
                        },
                        textAlign = TextAlign.Center,
                    )

                    week.state == CycleWeekState.COMPLETED -> Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(12.dp),
                    )

                    else -> Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

/** "SQ 200 → 205 [+5 KG]" — one lift's move between two cycles. */
@Composable
fun CycleComparisonRow(
    code: String,
    before: String,
    after: String,
    change: String,
    modifier: Modifier = Modifier,
    accent: Color = GriffGymTheme.colors.outlineStrong,
    changeColor: Color = GriffGymTheme.colors.primary,
) {
    val colors = GriffGymTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.surfaceLowest),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = before,
                    style = GriffGymTheme.typography.dataSmall,
                    color = colors.textTertiary,
                )
                Text(
                    text = " → ",
                    style = GriffGymTheme.typography.dataSmall,
                    color = colors.textTertiary,
                )
                Text(
                    text = after,
                    style = GriffGymTheme.typography.data,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = change,
                    style = GriffGymTheme.typography.labelSmall,
                    color = changeColor,
                )
            }
        }
    }
}

/**
 * A rectangular choice: charcoal when idle, amber-edged and amber-lettered when picked.
 *
 * Deliberately not a Material chip or radio button — those read as a form, and this is the
 * same stamped-metal language as the rest of the app.
 */
@Composable
fun GriffGymOptionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = GriffGymTheme.colors
    val shape = GriffGymTheme.shapes.button
    val border = when {
        !enabled -> colors.outline
        selected -> colors.primary
        else -> colors.outlineStrong
    }
    val content = when {
        !enabled -> colors.textTertiary
        selected -> colors.primary
        else -> colors.textSecondary
    }

    Box(
        modifier = modifier
            .height(GriffGymTheme.dimens.touchTarget)
            .clip(shape)
            .background(
                if (selected) colors.primary.copy(alpha = SELECTED_TINT) else Color.Transparent,
            )
            .border(
                width = if (selected) {
                    GriffGymTheme.dimens.borderWidthStrong
                } else {
                    GriffGymTheme.dimens.borderWidth
                },
                color = border,
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = GriffGymTheme.typography.label,
            color = content,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

private const val SELECTED_TINT = 0.12f

@Preview(widthDp = 358, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun CycleWeekTimelinePreview() {
    GriffGymTheme {
        Column(
            Modifier
                .fillMaxWidth()
                .background(GriffGymTheme.colors.surface)
                .padding(16.dp),
        ) {
            CycleWeekTimeline(
                weeks = listOf(
                    CycleWeekUiModel(1, "ACCUMULATION", false, CycleWeekState.COMPLETED),
                    CycleWeekUiModel(2, "ACCUMULATION", false, CycleWeekState.COMPLETED),
                    CycleWeekUiModel(3, "INTENSIFICATION", false, CycleWeekState.CURRENT),
                    CycleWeekUiModel(4, "INTENSIFICATION", false, CycleWeekState.UPCOMING),
                    CycleWeekUiModel(5, "PEAK", false, CycleWeekState.UPCOMING),
                    CycleWeekUiModel(6, "DELOAD", true, CycleWeekState.UPCOMING),
                ),
            )
            Spacer(Modifier.height(20.dp))
            CycleComparisonRow(
                code = "SQ",
                before = "200",
                after = "205",
                change = "+5 KG",
                accent = GriffGymTheme.colors.squat,
            )
            Spacer(Modifier.height(8.dp))
            CycleComparisonRow(
                code = "BP",
                before = "150",
                after = "150",
                change = "KEPT",
                accent = GriffGymTheme.colors.bench,
                changeColor = GriffGymTheme.colors.textTertiary,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GriffGymOptionButton(
                    text = "+5 KG",
                    selected = true,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                GriffGymOptionButton(
                    text = "KEEP",
                    selected = false,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
