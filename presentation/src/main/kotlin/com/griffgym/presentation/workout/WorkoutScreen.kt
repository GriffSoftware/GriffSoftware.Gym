package com.griffgym.presentation.workout

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.domain.model.ExerciseType
import com.griffgym.presentation.components.CardHeader
import com.griffgym.presentation.components.ExerciseCard
import com.griffgym.presentation.components.ExerciseCardState
import com.griffgym.presentation.components.GriffGymBadge
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.GriffGymDashedButton
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.GriffGymSecondaryButton
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.components.MetricColumn
import com.griffgym.presentation.components.SetRowState
import com.griffgym.presentation.components.StatusBadge
import com.griffgym.presentation.components.WorkoutUiStatus
import com.griffgym.presentation.theme.GriffGymTheme

@Composable
fun WorkoutRoute(
    onWorkoutFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { event ->
            when (event) {
                WorkoutNavigation.WorkoutFinished -> onWorkoutFinished()
            }
        }
    }

    WorkoutScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
fun WorkoutScreen(
    state: WorkoutUiState,
    onEvent: (WorkoutUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    val margin = GriffGymTheme.dimens.screenMargin

    when {
        state.isLoading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return
        }

        state.emptyState != null -> {
            EmptyWorkoutState(state.emptyState, onEvent, modifier)
            return
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = margin, end = margin, top = margin, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(GriffGymTheme.dimens.sectionSpacing),
    ) {
        state.header?.let { header ->
            item(key = "header") { WorkoutHeaderBlock(header) }
        }

        state.summary?.let { summary ->
            item(key = "summary") { SessionSummaryCard(summary) }
        }

        items(state.exercises, key = { it.exerciseLogId }) { exercise ->
            ExerciseCard(
                state = exercise,
                onWeightChange = { id, value -> onEvent(WorkoutUiEvent.WeightChanged(id, value)) },
                onRepsChange = { id, value -> onEvent(WorkoutUiEvent.RepsChanged(id, value)) },
                onRpeChange = { id, value -> onEvent(WorkoutUiEvent.RpeChanged(id, value)) },
                onToggleCompleted = { onEvent(WorkoutUiEvent.ToggleSetCompleted(it)) },
                onOpenSetDetails = { onEvent(WorkoutUiEvent.OpenSetDetails(it)) },
                onAddSet = { onEvent(WorkoutUiEvent.AddSet(it)) },
                readOnly = state.readOnly,
            )
        }

        if (!state.readOnly) {
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GriffGymDashedButton(
                        text = "ADD EXERCISE",
                        icon = Icons.Filled.Add,
                        onClick = { onEvent(WorkoutUiEvent.OpenExercisePicker) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GriffGymPrimaryButton(
                        text = "FINISH WORKOUT",
                        onClick = { onEvent(WorkoutUiEvent.RequestFinish) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GriffGymSecondaryButton(
                        text = "CANCEL WORKOUT",
                        onClick = { onEvent(WorkoutUiEvent.RequestCancel) },
                        color = colors.textTertiary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    WorkoutDialogs(state = state, onEvent = onEvent)
}

@Composable
private fun WorkoutHeaderBlock(header: WorkoutHeader) {
    val colors = GriffGymTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = header.title,
                    style = GriffGymTheme.typography.headline,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = header.subtitle,
                    style = GriffGymTheme.typography.body,
                    color = colors.textTertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (header.isDeload) {
                    GriffGymBadge("DELOAD", filled = true)
                    Spacer(Modifier.height(6.dp))
                }
                StatusBadge(header.status)
            }
        }
        Spacer(Modifier.height(12.dp))
        HairLine(color = colors.surfaceVariant)
    }
}

@Composable
private fun SessionSummaryCard(summary: WorkoutSummary) {
    GriffGymCard(accentBar = GriffGymTheme.colors.primary) {
        CardHeader(title = "Session Summary")
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MetricColumn("VOLUME", summary.volume, valueColor = GriffGymTheme.colors.primary)
            MetricColumn("DURATION", summary.duration)
            MetricColumn("SETS", summary.sets)
            MetricColumn("REPS", summary.reps)
        }
        if (!summary.notes.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = summary.notes,
                style = GriffGymTheme.typography.bodySmall,
                color = GriffGymTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun EmptyWorkoutState(
    empty: WorkoutEmptyState,
    onEvent: (WorkoutUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(GriffGymTheme.dimens.screenMargin),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = empty.title,
                style = GriffGymTheme.typography.displayMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = empty.subtitle,
                style = GriffGymTheme.typography.body,
                color = colors.textTertiary,
            )
            if (empty.canStart) {
                Spacer(Modifier.height(24.dp))
                GriffGymPrimaryButton(
                    text = "START WORKOUT",
                    onClick = { onEvent(WorkoutUiEvent.StartWorkout) },
                )
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 900, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun WorkoutScreenPreview() {
    GriffGymTheme {
        WorkoutScreen(state = PreviewWorkoutState, onEvent = {})
    }
}

internal val PreviewWorkoutState = WorkoutUiState(
    isLoading = false,
    sessionId = 1,
    header = WorkoutHeader(
        title = "WEEK 5, DAY I",
        subtitle = "Squat Focus / Bench Volume",
        status = WorkoutUiStatus.IN_PROGRESS,
        isDeload = false,
    ),
    exercises = listOf(
        ExerciseCardState(
            exerciseLogId = 1,
            position = 1,
            name = "Przysiad",
            type = ExerciseType.TOP,
            targetWeight = "200",
            targetReps = "1",
            targetRpe = "8",
            hasTarget = true,
            sets = listOf(SetRowState(1, 1, "200", "1", "8", completed = true, hasNotes = false)),
        ),
        ExerciseCardState(
            exerciseLogId = 2,
            position = 2,
            name = "Przysiad",
            type = ExerciseType.BACK_OFF,
            targetWeight = "185",
            targetReps = "2",
            targetRpe = "7",
            hasTarget = true,
            sets = listOf(
                SetRowState(2, 1, "185", "2", "8", completed = false, hasNotes = false),
                SetRowState(3, 2, "185", "2", "", completed = false, hasNotes = true),
            ),
        ),
    ),
)
