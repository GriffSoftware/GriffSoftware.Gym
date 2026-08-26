package com.griffgym.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.cycle.GetCurrentCycleUseCase
import com.griffgym.application.referencemax.GetReferenceMaxesUseCase
import com.griffgym.application.referencemax.UpdateReferenceMaxUseCase
import com.griffgym.application.stats.GetTrainingConsistencyUseCase
import com.griffgym.application.workout.GetCurrentWorkoutUseCase
import com.griffgym.application.workout.StartWorkoutUseCase
import com.griffgym.domain.model.CurrentWorkout
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.TrainingCycle
import com.griffgym.domain.model.TrainingDaySummary
import com.griffgym.domain.model.WorkoutSession
import com.griffgym.domain.model.WorkoutTemplate
import com.griffgym.presentation.format.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentWorkout: GetCurrentWorkoutUseCase,
    private val getCurrentCycle: GetCurrentCycleUseCase,
    private val startWorkout: StartWorkoutUseCase,
    private val getReferenceMaxes: GetReferenceMaxesUseCase,
    private val updateReferenceMax: UpdateReferenceMaxUseCase,
    private val getTrainingConsistency: GetTrainingConsistencyUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val localState = MutableStateFlow(LocalState())

    private val navigationChannel = Channel<HomeNavigation>(Channel.BUFFERED)
    val navigation = navigationChannel.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        getCurrentWorkout(),
        getCurrentCycle(),
        getReferenceMaxes(),
        getTrainingConsistency(),
        localState,
    ) { currentWorkout, cycle, referenceMaxes, consistency, local ->
        HomeUiState(
            isLoading = false,
            hero = currentWorkout.toHeroState(cycle),
            volumeTrend = buildVolumeTrend(consistency),
            referenceMaxes = referenceMaxes.map {
                ReferenceMaxItem(
                    category = it.category,
                    code = Format.categoryShort(it.category),
                    weight = it.weight.format(),
                )
            },
            editingReferenceMax = local.editor,
            message = local.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState(),
    )

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            HomeUiEvent.StartWorkout -> start()
            HomeUiEvent.ContinueWorkout -> continueWorkout()
            is HomeUiEvent.EditReferenceMax -> openEditor(event.category)
            is HomeUiEvent.ReferenceMaxInputChanged -> localState.update { state ->
                state.copy(editor = state.editor?.copy(input = event.input, error = null))
            }
            HomeUiEvent.ConfirmReferenceMax -> confirmReferenceMax()
            HomeUiEvent.DismissReferenceMaxEditor -> localState.update { it.copy(editor = null) }
            HomeUiEvent.MessageShown -> localState.update { it.copy(message = null) }
        }
    }

    private fun start() {
        viewModelScope.launch {
            startWorkout()
                .onSuccess { navigationChannel.send(HomeNavigation.OpenWorkout(it)) }
                .onFailure { error ->
                    localState.update { it.copy(message = error.message ?: "Could not start workout") }
                }
        }
    }

    private fun continueWorkout() {
        val sessionId = uiState.value.hero?.activeSessionId ?: return
        viewModelScope.launch { navigationChannel.send(HomeNavigation.OpenWorkout(sessionId)) }
    }

    private fun openEditor(category: ExerciseCategory) {
        val current = uiState.value.referenceMaxes.firstOrNull { it.category == category }
        localState.update {
            it.copy(
                editor = ReferenceMaxEditor(
                    category = category,
                    label = Format.categoryLabel(category),
                    input = current?.weight.orEmpty(),
                ),
            )
        }
    }

    private fun confirmReferenceMax() {
        val editor = localState.value.editor ?: return
        viewModelScope.launch {
            updateReferenceMax(editor.category, editor.input)
                .onSuccess { localState.update { it.copy(editor = null) } }
                .onFailure { error ->
                    localState.update { state ->
                        state.copy(
                            editor = state.editor?.copy(
                                error = error.message ?: "Enter a valid weight",
                            ),
                        )
                    }
                }
        }
    }

    /**
     * The cycle label is carried on every hero state, not just the completed one: knowing
     * which block a session belongs to is context the lifter should never have to leave the
     * screen to find.
     */
    private fun CurrentWorkout.toHeroState(cycle: TrainingCycle?): HeroCardState {
        val cycleLabel = cycle?.label
        return when (this) {
            is CurrentWorkout.Active -> session.toHeroState(cycleLabel)
            is CurrentWorkout.Planned -> template.toHeroState(cycleLabel)

            is CurrentWorkout.CycleCompleted -> HeroCardState(
                weekNumber = 0,
                dayNumber = 0,
                title = "Every week of this cycle is behind you. Decide where the next one starts.",
                isDeload = false,
                mode = HeroMode.CYCLE_COMPLETE,
                exercises = emptyList(),
                cycleLabel = this.cycle.label,
            )

            CurrentWorkout.NoProgram -> HeroCardState(
                weekNumber = 0,
                dayNumber = 0,
                title = "There is no training block on this device yet.",
                isDeload = false,
                mode = HeroMode.NO_PROGRAM,
                exercises = emptyList(),
            )
        }
    }

    private fun WorkoutTemplate.toHeroState(cycleLabel: String?) = HeroCardState(
        weekNumber = weekNumber,
        dayNumber = dayNumber,
        title = title,
        isDeload = isDeload,
        mode = HeroMode.READY,
        cycleLabel = cycleLabel,
        exercises = mainLifts.map { exercise ->
            HeroExercise(
                name = exercise.exercise.name,
                badge = Format.exerciseType(exercise.type),
                isTopSet = exercise.type == ExerciseType.TOP,
                scheme = exercise.scheme?.format().orEmpty(),
            )
        },
    )

    private fun WorkoutSession.toHeroState(cycleLabel: String?) = HeroCardState(
        weekNumber = weekNumber,
        dayNumber = dayNumber,
        title = title,
        isDeload = isDeload,
        mode = HeroMode.IN_PROGRESS,
        cycleLabel = cycleLabel,
        activeSessionId = id,
        exercises = exercises
            .filter { it.type.isMainLift }
            .map { exercise ->
                HeroExercise(
                    name = exercise.exercise.name,
                    badge = Format.exerciseType(exercise.type),
                    isTopSet = exercise.type == ExerciseType.TOP,
                    scheme = exercise.plannedScheme?.format().orEmpty(),
                )
            },
    )

    /**
     * The last seven days scaled against the heaviest of them, so the chart always uses
     * its full height regardless of how big the block's tonnage happens to be.
     */
    private fun buildVolumeTrend(consistency: List<TrainingDaySummary>): List<VolumeTrendDay> {
        val today = LocalDate.now(clock)
        val window = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val byDate = consistency.associateBy { it.date }
        val peak = window.mapNotNull { byDate[it]?.volume?.kilograms }.maxOrNull() ?: 0.0

        return window.map { date ->
            val volume = byDate[date]?.volume?.kilograms
            VolumeTrendDay(
                label = date.dayOfWeek
                    .getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
                    .uppercase(Locale.ENGLISH),
                ratio = if (peak > 0.0 && volume != null) (volume / peak).toFloat() else 0f,
                isToday = date == today,
                trained = volume != null,
            )
        }
    }

    private data class LocalState(
        val editor: ReferenceMaxEditor? = null,
        val message: String? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
