package com.griffgym.presentation.cycles

import androidx.compose.runtime.Immutable
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.presentation.components.CycleWeekUiModel

/** Everything the CYCLES screen renders. */
@Immutable
data class CyclesUiState(
    val isLoading: Boolean = true,
    val active: ActiveCycleUiState? = null,
    val comparison: CycleComparisonUiState? = null,
    /** Newest first, the current cycle excluded. */
    val history: List<CycleHistoryItem> = emptyList(),
)

/**
 * The cycle the lifter is in, or the one they have just finished and not yet replaced.
 *
 * [isCompleted] is carried rather than derived from the week counts because a cycle is
 * finished when its last workout was logged, not when its weeks happen to add up.
 */
@Immutable
data class ActiveCycleUiState(
    val cycleId: Long,
    val label: String,
    val isCompleted: Boolean,
    /** "WEEK 3 OF 6", or "6 OF 6 WEEKS DONE" once there is no current week left. */
    val progressLabel: String,
    val workoutsLabel: String,
    val weeks: List<CycleWeekUiModel>,
    val referenceMaxes: List<CycleReferenceMaxItem>,
)

@Immutable
data class CycleReferenceMaxItem(
    val category: ExerciseCategory,
    val code: String,
    val weight: String,
)

/** "VS CYCLE 2" — what the current block was built on compared with the one before it. */
@Immutable
data class CycleComparisonUiState(
    val title: String,
    val lifts: List<CycleComparisonItem>,
)

@Immutable
data class CycleComparisonItem(
    val category: ExerciseCategory,
    val code: String,
    val before: String,
    val after: String,
    /** "+5 KG", "-2.5 KG" or "KEPT". */
    val change: String,
    val isChanged: Boolean,
)

/** One row of the PREVIOUS CYCLES list. */
@Immutable
data class CycleHistoryItem(
    val cycleId: Long,
    val label: String,
    val isCompleted: Boolean,
    val weeksLabel: String,
    /** "SQ 200 · BP 150 · DL 220". */
    val referenceMaxesLabel: String,
)
