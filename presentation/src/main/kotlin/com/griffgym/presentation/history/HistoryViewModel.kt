package com.griffgym.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.workout.GetWorkoutHistoryUseCase
import com.griffgym.presentation.components.WorkoutUiStatus
import com.griffgym.presentation.format.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    getWorkoutHistory: GetWorkoutHistoryUseCase,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = getWorkoutHistory()
        .map { sessions ->
            HistoryUiState(
                isLoading = false,
                sessions = sessions.map { session ->
                    HistoryItem(
                        sessionId = session.id,
                        date = Format.date(session.date),
                        title = Format.weekAndDay(session.weekNumber, session.dayNumber),
                        subtitle = session.title,
                        status = WorkoutUiStatus.from(session.status),
                        volume = Format.volume(session.totalVolume),
                        duration = Format.duration(session.duration),
                        sets = "${session.completedSets} / ${session.totalSets}",
                        isDeload = session.isDeload,
                    )
                },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HistoryUiState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
