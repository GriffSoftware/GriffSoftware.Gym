package com.griffgym.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.stats.GetOneRepMaxHistoryUseCase
import com.griffgym.application.stats.GetPersonalRecordsUseCase
import com.griffgym.application.stats.GetTrainingConsistencyUseCase
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.TrainingVolume
import com.griffgym.domain.model.sum
import com.griffgym.presentation.format.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    getOneRepMaxHistory: GetOneRepMaxHistoryUseCase,
    getPersonalRecords: GetPersonalRecordsUseCase,
    getTrainingConsistency: GetTrainingConsistencyUseCase,
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = combine(
        getOneRepMaxHistory(),
        getPersonalRecords(),
        getTrainingConsistency(),
    ) { history, records, consistency ->
        StatsUiState(
            isLoading = false,
            progression = ExerciseCategory.bigThree.map { category ->
                ProgressionSeries(
                    category = category,
                    label = Format.categoryLabel(category),
                    points = history[category].orEmpty().map {
                        ProgressionPoint(it.date, it.estimated.kilograms)
                    },
                )
            },
            personalRecords = records.map { record ->
                PersonalRecordItem(
                    category = record.category,
                    label = Format.categoryLabel(record.category),
                    actual = record.bestActual?.let { "${it.weight.format()} kg" },
                    actualDate = record.bestActual?.let { Format.date(it.achievedOn) },
                    estimated = record.bestEstimated?.let { "${it.weight.format()} kg" },
                    estimatedSource = record.bestEstimated
                        ?.takeIf { it.isEstimate }
                        ?.let { "from ${it.liftedWeight.format()} kg x ${it.reps}" },
                )
            },
            trainedDays = consistency.associate {
                it.date to TrainedDay(it.sessionId, it.hasPersonalRecord)
            },
            totalSessions = consistency.size,
            totalVolume = Format.volume(
                consistency.map { it.volume }.sum().takeIf { consistency.isNotEmpty() }
                    ?: TrainingVolume.ZERO,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = StatsUiState(),
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
