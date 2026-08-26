package com.griffgym.presentation.cycles

import com.griffgym.domain.model.CycleComparison
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.TrainingCycle
import com.griffgym.domain.model.TrainingCycleSummary
import com.griffgym.presentation.components.CycleWeekState
import com.griffgym.presentation.components.CycleWeekUiModel
import com.griffgym.presentation.format.Format

/**
 * Turns cycle domain values into what the cycles screens draw.
 *
 * Kept out of the ViewModels because all three of them — overview, detail and review —
 * present the same facts, and a shared function is the only way "week 6 is the deload" and
 * "SQ 200 · BP 150 · DL 220" stay identical wherever they appear.
 */

/**
 * The six week bar.
 *
 * Exactly one week may be CURRENT: the first unfinished one, and only while the cycle is
 * still being trained. A finished cycle has no current week even if a session inside it was
 * skipped, because there is nothing left to train.
 */
internal fun TrainingCycleSummary.toWeekUiModels(): List<CycleWeekUiModel> {
    val currentWeek = if (cycle.isCompleted) null else currentWeekNumber
    return weeks.map { week ->
        CycleWeekUiModel(
            weekNumber = week.weekNumber,
            label = week.label,
            isDeload = week.isDeload,
            state = when {
                week.weekNumber == currentWeek -> CycleWeekState.CURRENT
                week.isComplete -> CycleWeekState.COMPLETED
                // A cycle the lifter walked away from mid-week still reads as done: its
                // weeks are behind them either way, and marking them "upcoming" would
                // invite them back into a block that no longer has a next workout.
                cycle.isCompleted -> CycleWeekState.COMPLETED
                else -> CycleWeekState.UPCOMING
            },
        )
    }
}

internal fun TrainingCycleSummary.progressLabel(): String = when (val week = currentWeekNumber) {
    null -> "$weekCount OF $weekCount WEEKS DONE"
    else -> "WEEK $week OF $weekCount"
}

/** "14/18 WORKOUTS" — counted from completed sessions, never from a stored tally. */
internal fun TrainingCycleSummary.workoutsLabel(): String =
    "$completedWorkouts/$plannedWorkouts WORKOUTS"

internal fun ReferenceMaxSnapshot.toItems(): List<CycleReferenceMaxItem> =
    byCategory.map { (category, weight) ->
        CycleReferenceMaxItem(
            category = category,
            code = Format.categoryShort(category),
            weight = weight.format(),
        )
    }

/** "SQ 200 · BP 150 · DL 220" — the whole snapshot on one line, for a history row. */
internal fun ReferenceMaxSnapshot.toCompactLabel(): String =
    byCategory.entries.joinToString(" · ") { (category, weight) ->
        "${Format.categoryShort(category)} ${weight.format()}"
    }

internal fun TrainingCycle.toHistoryItem(weeksLabel: String): CycleHistoryItem = CycleHistoryItem(
    cycleId = id,
    label = label,
    isCompleted = isCompleted,
    weeksLabel = weeksLabel,
    referenceMaxesLabel = referenceMaxes.toCompactLabel(),
)

internal fun CycleComparison.toUiState(): CycleComparisonUiState = CycleComparisonUiState(
    title = "VS ${previous.label}",
    lifts = lifts.map { lift ->
        CycleComparisonItem(
            category = lift.category,
            code = Format.categoryShort(lift.category),
            before = lift.before.format(),
            after = lift.after.format(),
            // "KEPT" rather than "0 KG": holding a max is a decision, and a zero reads like
            // a number that failed to load.
            change = if (lift.delta.isZero) "KEPT" else "${lift.delta.format()} KG",
            isChanged = !lift.delta.isZero,
        )
    },
)
