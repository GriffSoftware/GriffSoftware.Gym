package com.griffgym.presentation.history

import androidx.compose.runtime.Immutable
import com.griffgym.presentation.components.WorkoutUiStatus

@Immutable
data class HistoryUiState(
    val isLoading: Boolean = true,
    val sessions: List<HistoryItem> = emptyList(),
)

@Immutable
data class HistoryItem(
    val sessionId: Long,
    val date: String,
    val title: String,
    val subtitle: String,
    val status: WorkoutUiStatus,
    val volume: String,
    val duration: String,
    val sets: String,
    val isDeload: Boolean,
)
