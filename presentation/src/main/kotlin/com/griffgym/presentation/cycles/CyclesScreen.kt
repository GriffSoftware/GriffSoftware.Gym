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
import com.griffgym.presentation.components.CycleComparisonRow
import com.griffgym.presentation.components.CycleWeekState
import com.griffgym.presentation.components.CycleWeekTimeline
import com.griffgym.presentation.components.CycleWeekUiModel
import com.griffgym.presentation.components.GriffGymBadge
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.components.ReferenceMaxRow
import com.griffgym.presentation.components.accentFor
import com.griffgym.presentation.theme.GriffGymTheme

@Composable
fun CyclesRoute(
    onBack: () -> Unit,
    onOpenCycle: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CyclesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CyclesScreen(state = state, onBack = onBack, onOpenCycle = onOpenCycle, modifier = modifier)
}

@Composable
fun CyclesScreen(
    state: CyclesUiState,
    onBack: () -> Unit,
    onOpenCycle: (Long) -> Unit,
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
                breadcrumb = "TRAINING",
                title = "CYCLES",
                subtitle = "BUILD. RECOVER. PROGRESS.",
                onBack = onBack,
            )
        }

        val active = state.active
        if (active == null) {
            item(key = "empty") {
                Text(
                    text = "No training cycle on this device yet. Setting up your reference " +
                        "maxes builds the first one.",
                    style = GriffGymTheme.typography.body,
                    color = colors.textTertiary,
                )
            }
        } else {
            item(key = "active") { ActiveCycleCard(active) }
            item(key = "maxes") { CycleReferenceMaxCard(active) }
        }

        state.comparison?.let { comparison ->
            item(key = "comparison") { CycleComparisonCard(comparison) }
        }

        if (state.history.isNotEmpty()) {
            item(key = "history-title") {
                Text(
                    text = "PREVIOUS CYCLES",
                    style = GriffGymTheme.typography.label,
                    color = colors.textTertiary,
                )
            }
            items(state.history, key = { it.cycleId }) { item ->
                CycleHistoryCard(item = item, onClick = { onOpenCycle(item.cycleId) })
            }
        }
    }
}

@Composable
private fun ActiveCycleCard(active: ActiveCycleUiState) {
    val colors = GriffGymTheme.colors

    GriffGymCard(accentBar = colors.primary) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = active.label,
                    style = GriffGymTheme.typography.displayMedium,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = active.progressLabel,
                    style = GriffGymTheme.typography.label,
                    color = colors.primary,
                )
            }
            GriffGymBadge(
                text = if (active.isCompleted) "COMPLETED" else "ACTIVE",
                filled = active.isCompleted,
                color = colors.primary,
            )
        }

        Spacer(Modifier.height(16.dp))
        CycleWeekTimeline(weeks = active.weeks)

        Spacer(Modifier.height(14.dp))
        HairLine()
        Spacer(Modifier.height(12.dp))
        Text(
            text = active.workoutsLabel,
            style = GriffGymTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun CycleReferenceMaxCard(active: ActiveCycleUiState) {
    val colors = GriffGymTheme.colors

    GriffGymCard {
        CardHeader(title = "CYCLE REFERENCE MAX", titleColor = colors.textSecondary)
        Spacer(Modifier.height(14.dp))
        active.referenceMaxes.forEachIndexed { index, item ->
            // Deliberately not tappable: this is the frozen snapshot the block was
            // calculated from. Today's numbers are edited on Home.
            ReferenceMaxRow(
                code = item.code,
                weight = item.weight,
                accent = colors.accentFor(item.category),
            )
            if (index != active.referenceMaxes.lastIndex) Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Used to calculate this training block.",
            style = GriffGymTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun CycleComparisonCard(comparison: CycleComparisonUiState) {
    val colors = GriffGymTheme.colors

    GriffGymCard {
        CardHeader(title = comparison.title, titleColor = colors.textSecondary)
        Spacer(Modifier.height(14.dp))
        comparison.lifts.forEachIndexed { index, lift ->
            CycleComparisonRow(
                code = lift.code,
                before = lift.before,
                after = lift.after,
                change = lift.change,
                accent = colors.accentFor(lift.category),
                changeColor = if (lift.isChanged) colors.primary else colors.textTertiary,
            )
            if (index != comparison.lifts.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CycleHistoryCard(item: CycleHistoryItem, onClick: () -> Unit) {
    val colors = GriffGymTheme.colors

    GriffGymCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = GriffGymTheme.typography.title,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.weeksLabel,
                    style = GriffGymTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            if (item.isCompleted) GriffGymBadge("COMPLETED", color = colors.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        HairLine()
        Spacer(Modifier.height(10.dp))
        Text(
            text = item.referenceMaxesLabel,
            style = GriffGymTheme.typography.data,
            color = colors.textSecondary,
        )
    }
}

private val PreviewActiveCycle = ActiveCycleUiState(
    cycleId = 3,
    label = "CYCLE 3",
    isCompleted = false,
    progressLabel = "WEEK 3 OF 6",
    workoutsLabel = "7/18 WORKOUTS",
    weeks = listOf(
        CycleWeekUiModel(1, "ACCUMULATION", false, CycleWeekState.COMPLETED),
        CycleWeekUiModel(2, "ACCUMULATION", false, CycleWeekState.COMPLETED),
        CycleWeekUiModel(3, "INTENSIFICATION", false, CycleWeekState.CURRENT),
        CycleWeekUiModel(4, "INTENSIFICATION", false, CycleWeekState.UPCOMING),
        CycleWeekUiModel(5, "PEAK", false, CycleWeekState.UPCOMING),
        CycleWeekUiModel(6, "DELOAD", true, CycleWeekState.UPCOMING),
    ),
    referenceMaxes = listOf(
        CycleReferenceMaxItem(ExerciseCategory.SQUAT, "SQ", "210"),
        CycleReferenceMaxItem(ExerciseCategory.DEADLIFT, "DL", "225"),
        CycleReferenceMaxItem(ExerciseCategory.BENCH_PRESS, "BP", "170"),
    ),
)

private val PreviewCyclesState = CyclesUiState(
    isLoading = false,
    active = PreviewActiveCycle,
    comparison = CycleComparisonUiState(
        title = "VS CYCLE 2",
        lifts = listOf(
            CycleComparisonItem(ExerciseCategory.SQUAT, "SQ", "205", "210", "+5 KG", true),
            CycleComparisonItem(ExerciseCategory.DEADLIFT, "DL", "227.5", "225", "-2.5 KG", true),
            CycleComparisonItem(ExerciseCategory.BENCH_PRESS, "BP", "170", "170", "KEPT", false),
        ),
    ),
    history = listOf(
        CycleHistoryItem(2, "CYCLE 2", true, "6/6 WEEKS", "SQ 205 · DL 227.5 · BP 170"),
        CycleHistoryItem(1, "CYCLE 1", true, "6/6 WEEKS", "SQ 200 · DL 222.5 · BP 167.5"),
    ),
)

@Preview(widthDp = 390, heightDp = 1100, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun CyclesScreenPreview() {
    GriffGymTheme {
        CyclesScreen(state = PreviewCyclesState, onBack = {}, onOpenCycle = {})
    }
}

@Preview(widthDp = 390, heightDp = 700, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun CyclesScreenDeloadWeekPreview() {
    GriffGymTheme {
        CyclesScreen(
            state = PreviewCyclesState.copy(
                active = PreviewActiveCycle.copy(
                    progressLabel = "WEEK 6 OF 6",
                    workoutsLabel = "16/18 WORKOUTS",
                    weeks = PreviewActiveCycle.weeks.map { week ->
                        week.copy(
                            state = when (week.weekNumber) {
                                6 -> CycleWeekState.CURRENT
                                else -> CycleWeekState.COMPLETED
                            },
                        )
                    },
                ),
                comparison = null,
                history = emptyList(),
            ),
            onBack = {},
            onOpenCycle = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 500, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun CyclesScreenEmptyPreview() {
    GriffGymTheme {
        CyclesScreen(state = CyclesUiState(isLoading = false), onBack = {}, onOpenCycle = {})
    }
}
