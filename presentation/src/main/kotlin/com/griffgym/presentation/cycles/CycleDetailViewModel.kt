package com.griffgym.presentation.cycles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.cycle.CycleDetail
import com.griffgym.application.cycle.GetCycleDetailUseCase
import com.griffgym.domain.model.CycleWeekProgress
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.TrainingWeek
import com.griffgym.domain.model.WorkoutTemplate
import com.griffgym.presentation.format.Format
import com.griffgym.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One historical cycle, read once.
 *
 * A finished cycle cannot change, so this deliberately does not observe anything: a single
 * suspending read is both cheaper and a truer description of what is being shown.
 */
@HiltViewModel
class CycleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCycleDetail: GetCycleDetailUseCase,
) : ViewModel() {

    private val cycleId: Long? = savedStateHandle.get<String>(Routes.CYCLE_ID_ARG)?.toLongOrNull()

    private val state = MutableStateFlow(CycleDetailUiState())
    val uiState: StateFlow<CycleDetailUiState> = state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val id = cycleId
        if (id == null) {
            state.value = CycleDetailUiState(isLoading = false, error = "This cycle could not be opened.")
            return
        }
        viewModelScope.launch {
            val detail = runCatching { getCycleDetail(id) }.getOrNull()
            state.value = detail?.toUiState()
                ?: CycleDetailUiState(isLoading = false, error = "This cycle is no longer on this device.")
        }
    }

    private fun CycleDetail.toUiState(): CycleDetailUiState {
        val progressByWeek = summary.weeks.associateBy { it.weekNumber }
        return CycleDetailUiState(
            isLoading = false,
            cycle = CycleDetailHeaderUiState(
                label = summary.cycle.label,
                isCompleted = summary.cycle.isCompleted,
                progressLabel = summary.progressLabel(),
                workoutsLabel = summary.workoutsLabel(),
                timeline = summary.toWeekUiModels(),
                referenceMaxes = summary.cycle.referenceMaxes.toItems(),
            ),
            weeks = program
                ?.weeks
                ?.sortedBy { it.weekNumber }
                ?.map { week -> week.toUiState(progressByWeek[week.weekNumber]) }
                .orEmpty(),
        )
    }

    private fun TrainingWeek.toUiState(progress: CycleWeekProgress?) = CycleDetailWeek(
        weekNumber = weekNumber,
        label = label,
        isDeload = isDeload,
        workoutsLabel = progress
            ?.let { "${it.completedWorkouts}/${it.plannedWorkouts}" }
            ?: "${workouts.size}",
        days = workouts.sortedBy { it.dayNumber }.map { it.toUiState() },
    )

    private fun WorkoutTemplate.toUiState() = CycleDetailDay(
        dayId = id,
        label = "DAY ${Format.roman(dayNumber)}",
        title = title,
        mainLifts = mainLifts.map { exercise ->
            CycleDetailLift(
                name = exercise.exercise.name,
                badge = Format.exerciseType(exercise.type),
                isTopSet = exercise.type == ExerciseType.TOP,
                scheme = exercise.scheme?.format().orEmpty(),
            )
        },
    )
}
