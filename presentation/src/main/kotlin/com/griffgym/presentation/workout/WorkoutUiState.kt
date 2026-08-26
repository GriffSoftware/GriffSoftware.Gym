package com.griffgym.presentation.workout

import androidx.compose.runtime.Immutable
import com.griffgym.domain.model.Exercise
import com.griffgym.presentation.components.ExerciseCardState
import com.griffgym.presentation.components.WorkoutUiStatus

@Immutable
data class WorkoutUiState(
    val isLoading: Boolean = true,
    val sessionId: Long? = null,
    val header: WorkoutHeader? = null,
    val exercises: List<ExerciseCardState> = emptyList(),
    val readOnly: Boolean = false,
    val summary: WorkoutSummary? = null,
    val setDetails: SetDetailsState? = null,
    val exercisePicker: ExercisePickerState? = null,
    val confirmFinish: Boolean = false,
    val confirmCancel: Boolean = false,
    /** Shown when the log tab is opened and nothing has been started yet. */
    val emptyState: WorkoutEmptyState? = null,
    val message: String? = null,
)

@Immutable
data class WorkoutHeader(
    val title: String,
    val subtitle: String,
    val status: WorkoutUiStatus,
    val isDeload: Boolean,
)

@Immutable
data class WorkoutSummary(
    val volume: String,
    val duration: String,
    val sets: String,
    val reps: String,
    val notes: String?,
)

@Immutable
data class SetDetailsState(
    val setLogId: Long,
    val exerciseName: String,
    val setIndex: Int,
    val notes: String,
    val canRemove: Boolean,
)

@Immutable
data class ExercisePickerState(
    val exercises: List<Exercise>,
    val query: String = "",
) {
    val filtered: List<Exercise>
        get() = if (query.isBlank()) {
            exercises
        } else {
            exercises.filter { it.name.contains(query, ignoreCase = true) }
        }
}

@Immutable
data class WorkoutEmptyState(
    val title: String,
    val subtitle: String,
    val canStart: Boolean,
)

sealed interface WorkoutUiEvent {
    data class WeightChanged(val setLogId: Long, val value: String) : WorkoutUiEvent
    data class RepsChanged(val setLogId: Long, val value: String) : WorkoutUiEvent
    data class RpeChanged(val setLogId: Long, val value: String) : WorkoutUiEvent
    data class ToggleSetCompleted(val setLogId: Long) : WorkoutUiEvent

    data class OpenSetDetails(val setLogId: Long) : WorkoutUiEvent
    data class SetNotesChanged(val notes: String) : WorkoutUiEvent
    data object SaveSetNotes : WorkoutUiEvent
    data object RemoveSet : WorkoutUiEvent
    data object DismissSetDetails : WorkoutUiEvent

    data class AddSet(val exerciseLogId: Long) : WorkoutUiEvent
    data object OpenExercisePicker : WorkoutUiEvent
    data class ExerciseQueryChanged(val query: String) : WorkoutUiEvent
    data class AddExercise(val exerciseId: Long) : WorkoutUiEvent
    data object DismissExercisePicker : WorkoutUiEvent

    data object StartWorkout : WorkoutUiEvent
    data object RequestFinish : WorkoutUiEvent
    data object ConfirmFinish : WorkoutUiEvent
    data object DismissFinish : WorkoutUiEvent
    data object RequestCancel : WorkoutUiEvent
    data object ConfirmCancel : WorkoutUiEvent
    data object DismissCancel : WorkoutUiEvent
    data object MessageShown : WorkoutUiEvent
}

sealed interface WorkoutNavigation {
    data object WorkoutFinished : WorkoutNavigation
}
