package com.griffgym.presentation.cycles

import androidx.compose.runtime.Immutable
import com.griffgym.presentation.components.CycleWeekUiModel

/**
 * A cycle exactly as it was trained.
 *
 * Nothing on this screen is editable, so there is no event type and no draft state: a
 * finished cycle is history, and history that can be edited is not history.
 */
@Immutable
data class CycleDetailUiState(
    val isLoading: Boolean = true,
    val cycle: CycleDetailHeaderUiState? = null,
    val weeks: List<CycleDetailWeek> = emptyList(),
    /** Set when the cycle could not be read at all — a stale link, or a deleted cycle. */
    val error: String? = null,
)

@Immutable
data class CycleDetailHeaderUiState(
    val label: String,
    val isCompleted: Boolean,
    val progressLabel: String,
    val workoutsLabel: String,
    val timeline: List<CycleWeekUiModel>,
    val referenceMaxes: List<CycleReferenceMaxItem>,
)

@Immutable
data class CycleDetailWeek(
    val weekNumber: Int,
    val label: String,
    val isDeload: Boolean,
    val workoutsLabel: String,
    val days: List<CycleDetailDay>,
)

@Immutable
data class CycleDetailDay(
    val dayId: Long,
    /** "DAY I". */
    val label: String,
    val title: String,
    val mainLifts: List<CycleDetailLift>,
)

@Immutable
data class CycleDetailLift(
    val name: String,
    val badge: String,
    val isTopSet: Boolean,
    val scheme: String,
)
