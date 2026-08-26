package com.griffgym.presentation.cycles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.presentation.components.CardHeader
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.GriffGymOptionButton
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.components.MetricColumn
import com.griffgym.presentation.components.NumericInput
import com.griffgym.presentation.components.ReferenceMaxRow
import com.griffgym.presentation.components.accentFor
import com.griffgym.presentation.theme.GriffGymTheme

@Composable
fun CycleReviewRoute(
    onBack: () -> Unit,
    onNextCycleStarted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CycleReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { event ->
            when (event) {
                CycleReviewNavigation.NextCycleStarted -> onNextCycleStarted()
            }
        }
    }

    CycleReviewScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The closing report on a cycle and the one decision that follows it.
 *
 * Every card's biggest number is "NEXT" rather than the current max: the lifter is deciding
 * where the next block starts, not admiring where the last one did.
 */
@Composable
fun CycleReviewScreen(
    state: CycleReviewUiState,
    onEvent: (CycleReviewUiEvent) -> Unit,
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
                breadcrumb = "HOME",
                title = "CYCLE REVIEW",
                subtitle = "DECIDE WHERE THE NEXT ONE STARTS.",
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

        state.summary?.let { summary ->
            item(key = "summary") { CycleCompletionCard(summary) }
        }

        if (state.lifts.isNotEmpty()) {
            item(key = "progression-title") {
                Text(
                    text = "PROGRESSION",
                    style = GriffGymTheme.typography.label,
                    color = colors.textTertiary,
                )
            }

            items(state.lifts, key = { it.category }) { lift ->
                LiftProgressionCard(lift = lift, enabled = !state.isSaving, onEvent = onEvent)
            }

            item(key = "next-maxes") { NextReferenceMaxCard(state) }
            item(key = "start") { StartNextCycleSection(state = state, onEvent = onEvent) }
        }
    }
}

@Composable
private fun CycleCompletionCard(summary: CycleReviewSummary) {
    val colors = GriffGymTheme.colors

    GriffGymCard(accentBar = colors.primary) {
        Text(
            text = "${summary.cycleLabel} COMPLETE",
            style = GriffGymTheme.typography.displayMedium,
            color = colors.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Every scheduled unit of this block is logged.",
            style = GriffGymTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(14.dp))
        HairLine()
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricColumn(label = "LENGTH", value = summary.weeksLabel)
            MetricColumn(label = "WORKOUTS", value = summary.workoutsLabel)
        }
    }
}

@Composable
private fun LiftProgressionCard(
    lift: LiftProgressionUiState,
    enabled: Boolean,
    onEvent: (CycleReviewUiEvent) -> Unit,
) {
    val colors = GriffGymTheme.colors

    GriffGymCard(accentBar = colors.accentFor(lift.category)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = lift.label,
                    style = GriffGymTheme.typography.title,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "NOW ${lift.current} KG",
                    style = GriffGymTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "NEXT",
                    style = GriffGymTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = lift.next ?: "—",
                        style = GriffGymTheme.typography.dataHuge,
                        color = if (lift.next != null) colors.primary else colors.textTertiary,
                    )
                    Text(
                        text = " KG",
                        style = GriffGymTheme.typography.dataSmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GriffGymOptionButton(
                text = lift.increaseLabel,
                selected = lift.choice == ProgressionChoice.INCREASE,
                enabled = enabled,
                onClick = {
                    onEvent(
                        CycleReviewUiEvent.ChoiceSelected(lift.category, ProgressionChoice.INCREASE),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            GriffGymOptionButton(
                text = "KEEP",
                selected = lift.choice == ProgressionChoice.KEEP,
                enabled = enabled,
                onClick = {
                    onEvent(CycleReviewUiEvent.ChoiceSelected(lift.category, ProgressionChoice.KEEP))
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))
        // Quiet on purpose: a free number is the exception, not one of the two answers most
        // blocks end with.
        Text(
            text = if (lift.isCustom) "CUSTOM CHANGE" else "SET IT MYSELF",
            style = GriffGymTheme.typography.labelSmall,
            color = if (lift.isCustom) colors.primary else colors.textTertiary,
            modifier = Modifier
                .clickable(enabled = enabled) {
                    onEvent(
                        CycleReviewUiEvent.ChoiceSelected(
                            lift.category,
                            if (lift.isCustom) ProgressionChoice.INCREASE else ProgressionChoice.CUSTOM,
                        ),
                    )
                }
                .padding(vertical = 4.dp),
        )

        if (lift.isCustom) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumericInput(
                    value = lift.customInput,
                    onValueChange = {
                        onEvent(CycleReviewUiEvent.CustomDeltaChanged(lift.category, it))
                    },
                    modifier = Modifier.width(120.dp),
                    placeholder = "+0",
                    enabled = enabled,
                    // Dropping a max after illness or a break is an explicit decision the
                    // app has to be able to accept.
                    allowNegative = true,
                    isError = lift.error != null,
                    imeAction = ImeAction.Done,
                    textStyle = GriffGymTheme.typography.dataLarge,
                )
                Text(
                    text = "kg on top of ${lift.current}",
                    style = GriffGymTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }

        if (lift.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = lift.error,
                style = GriffGymTheme.typography.bodySmall,
                color = colors.error,
            )
        }
    }
}

@Composable
private fun NextReferenceMaxCard(state: CycleReviewUiState) {
    val colors = GriffGymTheme.colors

    GriffGymCard {
        CardHeader(
            title = "${state.nextCycleLabel} REFERENCE MAX",
            titleColor = colors.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        state.nextReferenceMaxes.forEachIndexed { index, item ->
            ReferenceMaxRow(
                code = item.code,
                weight = item.weight,
                accent = colors.accentFor(item.category),
            )
            if (index != state.nextReferenceMaxes.lastIndex) Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Every load in the new block is a percentage of these.",
            style = GriffGymTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun StartNextCycleSection(
    state: CycleReviewUiState,
    onEvent: (CycleReviewUiEvent) -> Unit,
) {
    val colors = GriffGymTheme.colors
    val status = state.status

    Column(Modifier.fillMaxWidth()) {
        if (status is CycleReviewStatus.Failed) {
            Text(
                text = status.message,
                style = GriffGymTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier
                    .clickable { onEvent(CycleReviewUiEvent.DismissError) }
                    .padding(bottom = 10.dp),
            )
        }
        GriffGymPrimaryButton(
            text = when (status) {
                CycleReviewStatus.Saving -> "BUILDING…"
                CycleReviewStatus.Completed -> "READY"
                else -> "START ${state.nextCycleLabel}"
            },
            onClick = { onEvent(CycleReviewUiEvent.StartNextCycle) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canStartNextCycle,
        )
    }
}

private fun previewLift(
    category: ExerciseCategory,
    label: String,
    code: String,
    current: String,
    increase: String,
    next: String?,
    choice: ProgressionChoice = ProgressionChoice.INCREASE,
    customInput: String = "",
    error: String? = null,
) = LiftProgressionUiState(
    category = category,
    label = label,
    code = code,
    current = current,
    choice = choice,
    increaseLabel = increase,
    customInput = customInput,
    next = next,
    error = error,
)

private val PreviewReviewState = CycleReviewUiState(
    isLoading = false,
    summary = CycleReviewSummary("CYCLE 3", "6 WEEKS", "18/18"),
    lifts = listOf(
        previewLift(ExerciseCategory.SQUAT, "SQUAT", "SQ", "210", "+5 KG", "215"),
        previewLift(
            category = ExerciseCategory.DEADLIFT,
            label = "DEADLIFT",
            code = "DL",
            current = "225",
            increase = "+5 KG",
            next = "222.5",
            choice = ProgressionChoice.CUSTOM,
            customInput = "-2.5",
        ),
        previewLift(
            category = ExerciseCategory.BENCH_PRESS,
            label = "BENCH PRESS",
            code = "BP",
            current = "170",
            increase = "+2.5 KG",
            next = "170",
            choice = ProgressionChoice.KEEP,
        ),
    ),
    nextCycleLabel = "CYCLE 4",
)

@Preview(widthDp = 390, heightDp = 1300, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun CycleReviewScreenPreview() {
    GriffGymTheme {
        CycleReviewScreen(state = PreviewReviewState, onEvent = {}, onBack = {})
    }
}

@Preview(widthDp = 390, heightDp = 1300, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun CycleReviewScreenInvalidPreview() {
    GriffGymTheme {
        CycleReviewScreen(
            state = PreviewReviewState.copy(
                lifts = PreviewReviewState.lifts.map { lift ->
                    if (lift.category == ExerciseCategory.DEADLIFT) {
                        lift.copy(
                            customInput = "-400",
                            next = null,
                            error = "That leaves nothing to train from",
                        )
                    } else {
                        lift
                    }
                },
                status = CycleReviewStatus.Failed("Could not start the next cycle."),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
