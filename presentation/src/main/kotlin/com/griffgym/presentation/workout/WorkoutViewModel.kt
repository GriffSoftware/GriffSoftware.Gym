package com.griffgym.presentation.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.exercise.GetExercisesUseCase
import com.griffgym.application.workout.AddExerciseToWorkoutUseCase
import com.griffgym.application.workout.AddSetUseCase
import com.griffgym.application.workout.CancelWorkoutUseCase
import com.griffgym.application.workout.CompleteWorkoutUseCase
import com.griffgym.application.workout.GetCurrentWorkoutUseCase
import com.griffgym.application.workout.GetWorkoutSessionUseCase
import com.griffgym.application.workout.RemoveSetUseCase
import com.griffgym.application.workout.SaveSetResultUseCase
import com.griffgym.application.workout.SetField
import com.griffgym.application.workout.SetInput
import com.griffgym.application.workout.SetValidation
import com.griffgym.application.workout.StartWorkoutUseCase
import com.griffgym.application.workout.UpdateSetResultUseCase
import com.griffgym.domain.model.CurrentWorkout
import com.griffgym.domain.model.ExerciseLog
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.SetLog
import com.griffgym.domain.model.WorkoutSession
import com.griffgym.presentation.components.ExerciseCardState
import com.griffgym.presentation.components.SetRowState
import com.griffgym.presentation.components.WorkoutUiStatus
import com.griffgym.presentation.format.Format
import com.griffgym.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives both the live log and the read-only view of a past session.
 *
 * With no `sessionId` argument it follows whatever session is currently in progress —
 * that is the LOG tab. With one, it pins to that session, which is how history is opened.
 */
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getCurrentWorkout: GetCurrentWorkoutUseCase,
    getWorkoutSession: GetWorkoutSessionUseCase,
    getExercises: GetExercisesUseCase,
    private val startWorkout: StartWorkoutUseCase,
    private val updateSetResult: UpdateSetResultUseCase,
    private val saveSetResult: SaveSetResultUseCase,
    private val addSetUseCase: AddSetUseCase,
    private val removeSetUseCase: RemoveSetUseCase,
    private val addExerciseUseCase: AddExerciseToWorkoutUseCase,
    private val completeWorkout: CompleteWorkoutUseCase,
    private val cancelWorkout: CancelWorkoutUseCase,
) : ViewModel() {

    private val pinnedSessionId: Long? = savedStateHandle.get<String>(Routes.SESSION_ID_ARG)?.toLongOrNull()

    private val localState = MutableStateFlow(LocalState())

    /** Mirror of the session currently on screen, so event handlers can read it directly. */
    private val latestSession = MutableStateFlow<WorkoutSession?>(null)

    private val navigationChannel = Channel<WorkoutNavigation>(Channel.BUFFERED)
    val navigation = navigationChannel.receiveAsFlow()

    private val sessionFlow: Flow<SessionSource> = if (pinnedSessionId != null) {
        getWorkoutSession(pinnedSessionId).map { SessionSource(session = it, plannedTitle = null) }
    } else {
        getCurrentWorkout().map { current ->
            when (current) {
                is CurrentWorkout.Active -> SessionSource(session = current.session, plannedTitle = null)
                is CurrentWorkout.Planned -> SessionSource(
                    session = null,
                    plannedTitle = Format.weekAndDay(
                        current.template.weekNumber,
                        current.template.dayNumber,
                    ) + " · " + current.template.title,
                )
                CurrentWorkout.ProgramCompleted -> SessionSource(session = null, plannedTitle = null)
            }
        }
    }

    val uiState: StateFlow<WorkoutUiState> = combine(
        sessionFlow.onEach { latestSession.value = it.session },
        getExercises(),
        localState,
    ) { source, exercises, local ->
        val session = source.session
        if (session == null) {
            WorkoutUiState(
                isLoading = false,
                emptyState = WorkoutEmptyState(
                    title = if (source.plannedTitle != null) "NOTHING STARTED YET" else "PROGRAM COMPLETE",
                    subtitle = source.plannedTitle
                        ?: "Every unit of this program has been logged.",
                    canStart = source.plannedTitle != null,
                ),
                message = local.message,
            )
        } else {
            session.toUiState(local, exercises)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = WorkoutUiState(),
    )

    fun onEvent(event: WorkoutUiEvent) {
        when (event) {
            is WorkoutUiEvent.WeightChanged -> editDraft(event.setLogId) { it.copy(weight = event.value) }
            is WorkoutUiEvent.RepsChanged -> editDraft(event.setLogId) { it.copy(reps = event.value) }
            is WorkoutUiEvent.RpeChanged -> editDraft(event.setLogId) { it.copy(rpe = event.value) }
            is WorkoutUiEvent.ToggleSetCompleted -> toggleCompleted(event.setLogId)

            is WorkoutUiEvent.OpenSetDetails -> openSetDetails(event.setLogId)
            is WorkoutUiEvent.SetNotesChanged -> localState.update { state ->
                state.copy(setDetails = state.setDetails?.copy(notes = event.notes))
            }
            WorkoutUiEvent.SaveSetNotes -> saveSetNotes()
            WorkoutUiEvent.RemoveSet -> removeSet()
            WorkoutUiEvent.DismissSetDetails -> localState.update { it.copy(setDetails = null) }

            is WorkoutUiEvent.AddSet -> viewModelScope.launch { addSetUseCase(event.exerciseLogId) }
            WorkoutUiEvent.OpenExercisePicker -> localState.update { it.copy(pickerOpen = true, pickerQuery = "") }
            is WorkoutUiEvent.ExerciseQueryChanged -> localState.update { it.copy(pickerQuery = event.query) }
            is WorkoutUiEvent.AddExercise -> addExercise(event.exerciseId)
            WorkoutUiEvent.DismissExercisePicker -> localState.update { it.copy(pickerOpen = false) }

            WorkoutUiEvent.StartWorkout -> start()
            WorkoutUiEvent.RequestFinish -> localState.update { it.copy(confirmFinish = true) }
            WorkoutUiEvent.ConfirmFinish -> finish()
            WorkoutUiEvent.DismissFinish -> localState.update { it.copy(confirmFinish = false) }
            WorkoutUiEvent.RequestCancel -> localState.update { it.copy(confirmCancel = true) }
            WorkoutUiEvent.ConfirmCancel -> cancel()
            WorkoutUiEvent.DismissCancel -> localState.update { it.copy(confirmCancel = false) }
            WorkoutUiEvent.MessageShown -> localState.update { it.copy(message = null) }
        }
    }

    /**
     * Every keystroke is written straight to Room.
     *
     * A local draft shadows the database value so the field never fights the round trip,
     * while the write itself is what makes a half-logged session survive the app being
     * killed between sets.
     */
    private fun editDraft(setLogId: Long, transform: (SetInput) -> SetInput) {
        val current = localState.value.drafts[setLogId] ?: currentInputFor(setLogId) ?: return
        val updated = transform(current)
        localState.update { it.copy(drafts = it.drafts + (setLogId to updated)) }
        persist(setLogId, updated, completedOf(setLogId))
    }

    private fun persist(setLogId: Long, input: SetInput, completed: Boolean) {
        viewModelScope.launch {
            val validation = updateSetResult(setLogId, input, completed)
            localState.update { state ->
                state.copy(
                    invalidFields = if (validation is SetValidation.Invalid) {
                        state.invalidFields + (setLogId to validation.invalidFields)
                    } else {
                        state.invalidFields - setLogId
                    },
                )
            }
        }
    }

    private fun toggleCompleted(setLogId: Long) {
        val input = localState.value.drafts[setLogId] ?: currentInputFor(setLogId) ?: return
        val alreadyCompleted = completedOf(setLogId)
        viewModelScope.launch {
            val validation = if (alreadyCompleted) {
                updateSetResult(setLogId, input, completed = false)
            } else {
                saveSetResult(setLogId, input)
            }
            when (validation) {
                is SetValidation.Invalid -> localState.update { state ->
                    state.copy(
                        invalidFields = state.invalidFields + (setLogId to validation.invalidFields),
                        message = validation.message(),
                    )
                }
                is SetValidation.Valid -> localState.update { state ->
                    state.copy(invalidFields = state.invalidFields - setLogId)
                }
            }
        }
    }

    private fun openSetDetails(setLogId: Long) {
        val session = currentSession() ?: return
        val exercise = session.exercises.firstOrNull { log -> log.sets.any { it.id == setLogId } } ?: return
        val set = exercise.sets.first { it.id == setLogId }
        localState.update {
            it.copy(
                setDetails = SetDetailsState(
                    setLogId = setLogId,
                    exerciseName = exercise.exercise.name,
                    setIndex = set.position,
                    notes = set.notes.orEmpty(),
                    canRemove = !session.isReadOnly && exercise.sets.size > 1,
                ),
            )
        }
    }

    private fun saveSetNotes() {
        val details = localState.value.setDetails ?: return
        val input = (localState.value.drafts[details.setLogId] ?: currentInputFor(details.setLogId))
            ?.copy(notes = details.notes) ?: return
        localState.update {
            it.copy(
                setDetails = null,
                drafts = it.drafts + (details.setLogId to input),
            )
        }
        persist(details.setLogId, input, completedOf(details.setLogId))
    }

    private fun removeSet() {
        val details = localState.value.setDetails ?: return
        viewModelScope.launch {
            removeSetUseCase(details.setLogId)
            localState.update {
                it.copy(setDetails = null, drafts = it.drafts - details.setLogId)
            }
        }
    }

    private fun addExercise(exerciseId: Long) {
        val sessionId = currentSession()?.id ?: return
        viewModelScope.launch {
            addExerciseUseCase(sessionId, exerciseId, ExerciseType.ACCESSORY)
                .onFailure { error ->
                    localState.update { it.copy(message = error.message) }
                }
            localState.update { it.copy(pickerOpen = false) }
        }
    }

    private fun start() {
        viewModelScope.launch {
            startWorkout().onFailure { error ->
                localState.update { it.copy(message = error.message ?: "Could not start workout") }
            }
        }
    }

    private fun finish() {
        val sessionId = currentSession()?.id ?: return
        viewModelScope.launch {
            completeWorkout(sessionId)
                .onSuccess {
                    localState.update { LocalState() }
                    navigationChannel.send(WorkoutNavigation.WorkoutFinished)
                }
                .onFailure { error ->
                    localState.update {
                        it.copy(confirmFinish = false, message = error.message)
                    }
                }
        }
    }

    private fun cancel() {
        val sessionId = currentSession()?.id ?: return
        viewModelScope.launch {
            cancelWorkout(sessionId)
            localState.update { LocalState() }
            navigationChannel.send(WorkoutNavigation.WorkoutFinished)
        }
    }

    private fun currentSession(): WorkoutSession? = latestSession.value

    private fun currentInputFor(setLogId: Long): SetInput? =
        currentSession()?.findSet(setLogId)?.toInput()

    private fun completedOf(setLogId: Long): Boolean =
        currentSession()?.findSet(setLogId)?.completed == true

    private fun WorkoutSession.findSet(setLogId: Long): SetLog? =
        exercises.firstNotNullOfOrNull { log -> log.sets.firstOrNull { it.id == setLogId } }

    private fun WorkoutSession.toUiState(
        local: LocalState,
        catalogue: List<com.griffgym.domain.model.Exercise>,
    ): WorkoutUiState = WorkoutUiState(
            isLoading = false,
            sessionId = id,
            header = WorkoutHeader(
                title = Format.weekAndDay(weekNumber, dayNumber),
                subtitle = title,
                status = WorkoutUiStatus.from(status),
                isDeload = isDeload,
            ),
            exercises = exercises.map { it.toCardState(local) },
            readOnly = isReadOnly,
            summary = if (isReadOnly) {
                WorkoutSummary(
                    volume = Format.volume(totalVolume),
                    duration = Format.duration(duration),
                    sets = "$completedSets / $totalSets",
                    reps = totalReps.toString(),
                    notes = notes,
                )
            } else {
                null
            },
            setDetails = local.setDetails,
            exercisePicker = if (local.pickerOpen) {
                ExercisePickerState(exercises = catalogue, query = local.pickerQuery)
            } else {
                null
            },
            confirmFinish = local.confirmFinish,
        confirmCancel = local.confirmCancel,
        message = local.message,
    )

    private fun ExerciseLog.toCardState(local: LocalState): ExerciseCardState {
        val scheme = plannedScheme
        val first = sets.firstOrNull()
        return ExerciseCardState(
            exerciseLogId = id,
            position = position,
            name = exercise.name,
            type = type,
            targetWeight = (scheme?.weight ?: first?.plannedWeight)?.format() ?: "—",
            targetReps = (scheme?.reps ?: first?.plannedReps)?.toString() ?: "—",
            targetRpe = (scheme?.targetRpe ?: first?.plannedRpe)?.format() ?: "—",
            hasTarget = first?.plannedReps != null || first?.plannedWeight != null,
            sets = sets.map { set ->
                val draft = local.drafts[set.id]
                val invalid = local.invalidFields[set.id].orEmpty()
                SetRowState(
                    setLogId = set.id,
                    index = set.position,
                    weight = draft?.weight ?: set.actualWeight?.format().orEmpty(),
                    reps = draft?.reps ?: set.actualReps?.toString().orEmpty(),
                    rpe = draft?.rpe ?: set.actualRpe?.format().orEmpty(),
                    completed = set.completed,
                    hasNotes = !set.notes.isNullOrBlank(),
                    weightInvalid = SetField.WEIGHT in invalid,
                    repsInvalid = SetField.REPS in invalid,
                    rpeInvalid = SetField.RPE in invalid,
                )
            },
        )
    }

    private fun SetLog.toInput() = SetInput(
        weight = actualWeight?.format().orEmpty(),
        reps = actualReps?.toString().orEmpty(),
        rpe = actualRpe?.format().orEmpty(),
        notes = notes,
    )

    private fun SetValidation.Invalid.message(): String = when {
        SetField.RPE in invalidFields -> "RPE must be between 1 and 10"
        SetField.WEIGHT in invalidFields && SetField.REPS in invalidFields ->
            "Enter a load and a rep count before ticking the set"
        SetField.WEIGHT in invalidFields -> "Enter a valid load"
        else -> "Enter a valid rep count"
    }

    private data class SessionSource(
        val session: WorkoutSession?,
        val plannedTitle: String?,
    )

    private data class LocalState(
        val drafts: Map<Long, SetInput> = emptyMap(),
        val invalidFields: Map<Long, Set<SetField>> = emptyMap(),
        val setDetails: SetDetailsState? = null,
        val pickerOpen: Boolean = false,
        val pickerQuery: String = "",
        val confirmFinish: Boolean = false,
        val confirmCancel: Boolean = false,
        val message: String? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
