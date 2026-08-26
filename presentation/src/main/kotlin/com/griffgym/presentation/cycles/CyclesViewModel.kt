package com.griffgym.presentation.cycles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.cycle.CycleOverview
import com.griffgym.application.cycle.GetCycleOverviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The CYCLES screen.
 *
 * Read-only by design: reference maxes are edited on Home and the next cycle is decided on
 * the review screen, so this one has no events at all. Everything it shows is derived from a
 * single flow, which is what keeps the active card, the comparison and the history list from
 * ever disagreeing about which cycle is current.
 */
@HiltViewModel
class CyclesViewModel @Inject constructor(
    getCycleOverview: GetCycleOverviewUseCase,
) : ViewModel() {

    val uiState: StateFlow<CyclesUiState> = getCycleOverview()
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = CyclesUiState(),
        )

    private fun CycleOverview.toUiState() = CyclesUiState(
        isLoading = false,
        active = current?.let { summary ->
            ActiveCycleUiState(
                cycleId = summary.cycle.id,
                label = summary.cycle.label,
                isCompleted = summary.cycle.isCompleted,
                progressLabel = summary.progressLabel(),
                workoutsLabel = summary.workoutsLabel(),
                weeks = summary.toWeekUiModels(),
                referenceMaxes = summary.cycle.referenceMaxes.toItems(),
            )
        },
        comparison = comparison?.toUiState(),
        history = previous.map { summary ->
            summary.cycle.toHistoryItem(
                weeksLabel = "${summary.completedWeeks}/${summary.weekCount} WEEKS",
            )
        },
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
