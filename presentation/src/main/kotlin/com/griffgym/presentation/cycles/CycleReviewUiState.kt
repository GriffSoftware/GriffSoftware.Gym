package com.griffgym.presentation.cycles

import androidx.compose.runtime.Immutable
import com.griffgym.domain.model.ExerciseCategory

/**
 * Where "START CYCLE N+1" is.
 *
 * There is deliberately no "calculating" phase. Applying three deltas to three maxes is a
 * pure, synchronous function of the fields on screen, so the next maxes are a *value* on
 * [LiftProgressionUiState] that updates as the lifter types — not a phase the flow passes
 * through. The same split first-run setup uses. Only the write is genuinely asynchronous.
 */
sealed interface CycleReviewStatus {
    data object Idle : CycleReviewStatus

    /** The new cycle, its block and its maxes are being written. */
    data object Saving : CycleReviewStatus

    /** Written. The screen leaves the back stack on this. */
    data object Completed : CycleReviewStatus

    data class Failed(val message: String) : CycleReviewStatus
}

@Immutable
data class CycleReviewUiState(
    val isLoading: Boolean = true,
    val summary: CycleReviewSummary? = null,
    val lifts: List<LiftProgressionUiState> = emptyList(),
    val nextCycleLabel: String = "",
    val status: CycleReviewStatus = CycleReviewStatus.Idle,
    /** Set when the cycle could not be read at all — nothing to review. */
    val error: String? = null,
) {
    val isSaving: Boolean get() = status == CycleReviewStatus.Saving

    /** Every lift has to land somewhere trainable before a block can be built from them. */
    val canStartNextCycle: Boolean
        get() = !isSaving && lifts.isNotEmpty() && lifts.all { it.next != null }

    /** The "CYCLE N+1 REFERENCE MAX" strip under the cards. */
    val nextReferenceMaxes: List<CycleReferenceMaxItem>
        get() = lifts.mapNotNull { lift ->
            lift.next?.let { CycleReferenceMaxItem(lift.category, lift.code, it) }
        }
}

/** What the cycle just finished actually amounted to. */
@Immutable
data class CycleReviewSummary(
    val cycleLabel: String,
    val weeksLabel: String,
    val workoutsLabel: String,
)

/** Which of the three progression options is selected for one lift. */
enum class ProgressionChoice { INCREASE, KEEP, CUSTOM }

@Immutable
data class LiftProgressionUiState(
    val category: ExerciseCategory,
    val label: String,
    val code: String,
    val current: String,
    val choice: ProgressionChoice,
    /** "+5 KG" — the suggestion from the domain progression policy. */
    val increaseLabel: String,
    val customInput: String,
    /** The resulting max, or null while the typed change cannot produce a trainable one. */
    val next: String?,
    val error: String? = null,
) {
    val isCustom: Boolean get() = choice == ProgressionChoice.CUSTOM
}

sealed interface CycleReviewUiEvent {
    data class ChoiceSelected(
        val category: ExerciseCategory,
        val choice: ProgressionChoice,
    ) : CycleReviewUiEvent

    data class CustomDeltaChanged(
        val category: ExerciseCategory,
        val value: String,
    ) : CycleReviewUiEvent

    data object StartNextCycle : CycleReviewUiEvent
    data object DismissError : CycleReviewUiEvent
}

/** One-shot instruction the screen consumes and forgets. */
sealed interface CycleReviewNavigation {
    /** The next cycle exists; the review has nothing left to say and must not be returned to. */
    data object NextCycleStarted : CycleReviewNavigation
}
