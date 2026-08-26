package com.griffgym.presentation.cycles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.presentation.components.CardHeader
import com.griffgym.presentation.components.CycleWeekState
import com.griffgym.presentation.components.CycleWeekTimeline
import com.griffgym.presentation.components.CycleWeekUiModel
import com.griffgym.presentation.components.DeloadBadgeLabel
import com.griffgym.presentation.components.GriffGymBadge
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.components.ReferenceMaxRow
import com.griffgym.presentation.components.TrainingSummaryRow
import com.griffgym.presentation.components.accentFor
import com.griffgym.presentation.theme.GriffGymTheme

@Composable
fun CycleDetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CycleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CycleDetailScreen(state = state, onBack = onBack, modifier = modifier)
}

/** A cycle the lifter has already trained. Read-only throughout: no edit affordances. */
@Composable
fun CycleDetailScreen(
    state: CycleDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    val margin = GriffGymTheme.dimens.screenMargin

    if (state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.primary)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = margin, end = margin, top = margin, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(GriffGymTheme.dimens.sectionSpacing),
    ) {
        item(key = "header") {
            CycleScreenHeader(
                breadcrumb = "CYCLES",
                title = state.cycle?.label ?: "CYCLE",
                subtitle = "TRAINED AS RECORDED.",
                onBack = onBack,
            )
        }

        if (state.error != null) {
            item(key = "error") {
                Text(
                    text = state.error,
                    style = GriffGymTheme.typography.body,
                    color = colors.textTertiary,
                )
            }
        }

        state.cycle?.let { cycle ->
            item(key = "summary") { CycleDetailSummaryCard(cycle) }
        }

        if (state.weeks.isNotEmpty()) {
            item(key = "plan-title") {
                Text(
                    text = "THE BLOCK",
                    style = GriffGymTheme.typography.label,
                    color = colors.textTertiary,
                )
            }
            items(state.weeks, key = { it.weekNumber }) { week -> CycleDetailWeekCard(week) }
        }
    }
}

@Composable
private fun CycleDetailSummaryCard(cycle: CycleDetailHeaderUiState) {
    val colors = GriffGymTheme.colors

    GriffGymCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = cycle.progressLabel,
                    style = GriffGymTheme.typography.headline,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = cycle.workoutsLabel,
                    style = GriffGymTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            if (cycle.isCompleted) GriffGymBadge("COMPLETED", color = colors.textSecondary)
        }

        Spacer(Modifier.height(16.dp))
        CycleWeekTimeline(weeks = cycle.timeline)

        Spacer(Modifier.height(16.dp))
        HairLine()
        Spacer(Modifier.height(14.dp))
        Text(
            text = "REFERENCE MAX",
            style = GriffGymTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(10.dp))
        cycle.referenceMaxes.forEachIndexed { index, item ->
            ReferenceMaxRow(
                code = item.code,
                weight = item.weight,
                accent = colors.accentFor(item.category),
            )
            if (index != cycle.referenceMaxes.lastIndex) Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "This block was calculated from these numbers.",
            style = GriffGymTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun CycleDetailWeekCard(week: CycleDetailWeek) {
    val colors = GriffGymTheme.colors

    GriffGymCard(accentBar = if (week.isDeload) colors.bench else null) {
        CardHeader(
            title = "WEEK ${week.weekNumber} · ${week.label}",
            titleColor = colors.textSecondary,
            action = {
                if (week.isDeload) {
                    GriffGymBadge(DeloadBadgeLabel, color = colors.bench)
                } else {
                    Text(
                        text = week.workoutsLabel,
                        style = GriffGymTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                }
            },
        )
        week.days.forEach { day ->
            Spacer(Modifier.height(14.dp))
            Text(
                text = "${day.label} · ${day.title}",
                style = GriffGymTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(2.dp))
            day.mainLifts.forEach { lift ->
                TrainingSummaryRow(
                    name = lift.name,
                    badge = lift.badge,
                    scheme = lift.scheme,
                    badgeFilled = lift.isTopSet,
                )
            }
        }
    }
}

private val PreviewCycleDetail = CycleDetailUiState(
    isLoading = false,
    cycle = CycleDetailHeaderUiState(
        label = "CYCLE 2",
        isCompleted = true,
        progressLabel = "6 OF 6 WEEKS DONE",
        workoutsLabel = "18/18 WORKOUTS",
        timeline = (1..6).map { week ->
            CycleWeekUiModel(
                weekNumber = week,
                label = if (week == 6) "DELOAD" else "WORK",
                isDeload = week == 6,
                state = CycleWeekState.COMPLETED,
            )
        },
        referenceMaxes = listOf(
            CycleReferenceMaxItem(ExerciseCategory.SQUAT, "SQ", "205"),
            CycleReferenceMaxItem(ExerciseCategory.DEADLIFT, "DL", "227.5"),
            CycleReferenceMaxItem(ExerciseCategory.BENCH_PRESS, "BP", "170"),
        ),
    ),
    weeks = listOf(
        CycleDetailWeek(
            weekNumber = 1,
            label = "ACCUMULATION",
            isDeload = false,
            workoutsLabel = "3/3",
            days = listOf(
                CycleDetailDay(
                    dayId = 1,
                    label = "DAY I",
                    title = "Squat Focus / Bench Volume",
                    mainLifts = listOf(
                        CycleDetailLift("Przysiad", "TOP", true, "1x3x182.5kg"),
                        CycleDetailLift("Przysiad", "BACK-OFF", false, "3x3x170kg"),
                        CycleDetailLift("Ławka", "VOLUME", false, "4x6x125kg"),
                    ),
                ),
            ),
        ),
        CycleDetailWeek(
            weekNumber = 6,
            label = "DELOAD",
            isDeload = true,
            workoutsLabel = "3/3",
            days = listOf(
                CycleDetailDay(
                    dayId = 16,
                    label = "DAY I",
                    title = "Deload — Squat / Bench",
                    mainLifts = listOf(
                        CycleDetailLift("Przysiad", "DELOAD", false, "3x3x102.5kg"),
                        CycleDetailLift("Ławka", "DELOAD", false, "3x5x85kg"),
                    ),
                ),
            ),
        ),
    ),
)

@Preview(widthDp = 390, heightDp = 1000, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun CycleDetailScreenPreview() {
    GriffGymTheme {
        CycleDetailScreen(state = PreviewCycleDetail, onBack = {})
    }
}

@Preview(widthDp = 390, heightDp = 300, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun CycleDetailScreenMissingPreview() {
    GriffGymTheme {
        CycleDetailScreen(
            state = CycleDetailUiState(
                isLoading = false,
                error = "This cycle is no longer on this device.",
            ),
            onBack = {},
        )
    }
}
