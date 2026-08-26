package com.griffgym.presentation.onboarding

import androidx.compose.runtime.Immutable
import com.griffgym.domain.model.ExerciseCategory

/**
 * The lifts first-run setup asks about, in the order it asks about them. Squat, bench and
 * deadlift open the sessions of day I, II and III respectively, which is the order the
 * lifter meets them in the block.
 */
val OnboardingLifts: List<ExerciseCategory> = listOf(
    ExerciseCategory.SQUAT,
    ExerciseCategory.BENCH_PRESS,
    ExerciseCategory.DEADLIFT,
)

/** How the lifter is telling us their one rep max. */
enum class OneRepMaxEntryMode { CALCULATOR, DIRECT }

/**
 * Where the whole flow is.
 *
 * There is deliberately no "calculating" state: the Epley estimate is a pure, synchronous
 * function of the two fields, so it belongs to [LiftStepUiState] as a value rather than to
 * the flow as a phase. Only building the program is genuinely asynchronous.
 */
sealed interface OnboardingStatus {
    data object Idle : OnboardingStatus
    data object Building : OnboardingStatus
    data object Completed : OnboardingStatus
    data class Failed(val message: String) : OnboardingStatus
}

@Immutable
data class OnboardingUiState(
    val steps: List<LiftStepUiState>,
    val summary: OnboardingSummaryUiState,
    val status: OnboardingStatus = OnboardingStatus.Idle,
) {
    fun step(index: Int): LiftStepUiState? = steps.getOrNull(index)
}

@Immutable
data class LiftStepUiState(
    val category: ExerciseCategory,
    val label: String,
    /** 1-based, for the "STEP 2 / 3" indicator. */
    val stepNumber: Int,
    val stepCount: Int,
    val mode: OneRepMaxEntryMode,
    val weightInput: String,
    val reps: Int,
    val oneRepMaxInput: String,
    /**
     * The already formatted value the confirm button would store, or null while the input
     * cannot produce one. Doubles as the enablement rule for that button.
     */
    val pendingOneRepMax: String?,
    /** Epley degrades past ten reps; the step says so rather than silently misleading. */
    val isEstimateReliable: Boolean,
    val confirmedOneRepMax: String?,
    val error: String?,
) {
    val canConfirm: Boolean get() = pendingOneRepMax != null
}

@Immutable
data class OnboardingSummaryUiState(
    val lifts: List<SummaryLiftUiState>,
    val isBuilding: Boolean = false,
    val error: String? = null,
) {
    /** Never let a block be generated from an incomplete set of maxes. */
    val canBuild: Boolean get() = !isBuilding && lifts.all { it.isValid }
}

@Immutable
data class SummaryLiftUiState(
    val category: ExerciseCategory,
    val label: String,
    val input: String,
    val error: String? = null,
) {
    val isValid: Boolean get() = error == null && input.isNotBlank()
}

sealed interface OnboardingUiEvent {
    data class ModeChanged(val category: ExerciseCategory, val mode: OneRepMaxEntryMode) : OnboardingUiEvent
    data class WeightChanged(val category: ExerciseCategory, val value: String) : OnboardingUiEvent
    data class RepsChanged(val category: ExerciseCategory, val value: Int) : OnboardingUiEvent
    data class OneRepMaxChanged(val category: ExerciseCategory, val value: String) : OnboardingUiEvent
    data class Confirm(val category: ExerciseCategory) : OnboardingUiEvent
    data class SummaryValueChanged(val category: ExerciseCategory, val value: String) : OnboardingUiEvent
    data object Build : OnboardingUiEvent
    data object DismissError : OnboardingUiEvent
}
