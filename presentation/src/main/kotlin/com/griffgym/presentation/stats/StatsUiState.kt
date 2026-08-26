package com.griffgym.presentation.stats

import androidx.compose.runtime.Immutable
import com.griffgym.domain.model.ExerciseCategory
import java.time.LocalDate

@Immutable
data class StatsUiState(
    val isLoading: Boolean = true,
    val progression: List<ProgressionSeries> = emptyList(),
    val personalRecords: List<PersonalRecordItem> = emptyList(),
    val trainedDays: Map<LocalDate, TrainedDay> = emptyMap(),
    val totalSessions: Int = 0,
    val totalVolume: String = "0 kg",
)

@Immutable
data class ProgressionSeries(
    val category: ExerciseCategory,
    val label: String,
    val points: List<ProgressionPoint>,
)

@Immutable
data class ProgressionPoint(val date: LocalDate, val estimated: Double)

@Immutable
data class PersonalRecordItem(
    val category: ExerciseCategory,
    val label: String,
    val actual: String?,
    val actualDate: String?,
    val estimated: String?,
    val estimatedSource: String?,
)

@Immutable
data class TrainedDay(val sessionId: Long, val hasPersonalRecord: Boolean)
