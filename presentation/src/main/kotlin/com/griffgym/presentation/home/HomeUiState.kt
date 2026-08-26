package com.griffgym.presentation.home

import androidx.compose.runtime.Immutable
import com.griffgym.domain.model.ExerciseCategory

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val hero: HeroCardState? = null,
    val volumeTrend: List<VolumeTrendDay> = emptyList(),
    val referenceMaxes: List<ReferenceMaxItem> = emptyList(),
    val editingReferenceMax: ReferenceMaxEditor? = null,
    val message: String? = null,
)

/** The card that answers "what am I doing today?". */
@Immutable
data class HeroCardState(
    val weekNumber: Int,
    val dayNumber: Int,
    val title: String,
    val isDeload: Boolean,
    val mode: HeroMode,
    val exercises: List<HeroExercise>,
    /** "CYCLE 3" — context above the week and day. Absent only before the first cycle. */
    val cycleLabel: String? = null,
    val activeSessionId: Long? = null,
)

enum class HeroMode {
    READY,
    IN_PROGRESS,

    /**
     * The cycle's last workout is logged and the next one has not been decided on. Kept
     * apart from the other modes because it is the one state whose call to action leaves
     * the training flow entirely.
     */
    CYCLE_COMPLETE,

    /** No plan at all — nothing to start and nothing to review. */
    NO_PROGRAM,
}

@Immutable
data class HeroExercise(
    val name: String,
    val badge: String?,
    val isTopSet: Boolean,
    val scheme: String,
)

@Immutable
data class VolumeTrendDay(
    val label: String,
    val ratio: Float,
    val isToday: Boolean,
    val trained: Boolean,
)

@Immutable
data class ReferenceMaxItem(
    val category: ExerciseCategory,
    val code: String,
    val weight: String,
)

@Immutable
data class ReferenceMaxEditor(
    val category: ExerciseCategory,
    val label: String,
    val input: String,
    val error: String? = null,
)

sealed interface HomeUiEvent {
    data object StartWorkout : HomeUiEvent
    data object ContinueWorkout : HomeUiEvent
    data class EditReferenceMax(val category: ExerciseCategory) : HomeUiEvent
    data class ReferenceMaxInputChanged(val input: String) : HomeUiEvent
    data object ConfirmReferenceMax : HomeUiEvent
    data object DismissReferenceMaxEditor : HomeUiEvent
    data object MessageShown : HomeUiEvent
}

/** One-shot navigation instructions the screen consumes and forgets. */
sealed interface HomeNavigation {
    data class OpenWorkout(val sessionId: Long) : HomeNavigation
}
