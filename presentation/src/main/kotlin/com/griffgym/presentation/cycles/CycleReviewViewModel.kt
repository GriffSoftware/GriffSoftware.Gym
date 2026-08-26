package com.griffgym.presentation.cycles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.cycle.CycleReview
import com.griffgym.application.cycle.GetCycleReviewUseCase
import com.griffgym.application.cycle.StartNextTrainingCycleUseCase
import com.griffgym.domain.model.CycleProgressionDecision
import com.griffgym.domain.model.DefaultCycleProgressionPolicy
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.LiftProgression
import com.griffgym.domain.model.ReferenceMaxChange
import com.griffgym.domain.model.ReferenceMaxDelta
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.Weight
import com.griffgym.presentation.format.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The end of a cycle: what it amounted to, and where the next one starts.
 *
 * The three lifts are decided independently — adding to the squat, holding the bench and
 * dropping the deadlift after a tweak is three ordinary decisions, not an inconsistency.
 * Nothing here creates a cycle on its own: the write happens only when the lifter presses
 * the button, and exactly once.
 */
@HiltViewModel
class CycleReviewViewModel @Inject constructor(
    private val getCycleReview: GetCycleReviewUseCase,
    private val startNextTrainingCycle: StartNextTrainingCycleUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(LocalState())

    private val navigationChannel = Channel<CycleReviewNavigation>(Channel.BUFFERED)
    val navigation = navigationChannel.receiveAsFlow()

    val uiState: StateFlow<CycleReviewUiState> = state
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = CycleReviewUiState(),
        )

    init {
        load()
    }

    fun onEvent(event: CycleReviewUiEvent) {
        when (event) {
            is CycleReviewUiEvent.ChoiceSelected -> state.update { local ->
                local.copy(choices = local.choices + (event.category to event.choice))
            }

            is CycleReviewUiEvent.CustomDeltaChanged -> state.update { local ->
                local.copy(customInputs = local.customInputs + (event.category to event.value))
            }

            CycleReviewUiEvent.StartNextCycle -> startNextCycle()
            CycleReviewUiEvent.DismissError -> state.update { it.copy(status = CycleReviewStatus.Idle) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            getCycleReview()
                .onSuccess { review -> state.update { it.withReview(review) } }
                .onFailure { error ->
                    state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "There is no cycle to review.",
                        )
                    }
                }
        }
    }

    /**
     * Guarded against a double tap: the second press finds the flow already saving and is
     * dropped, so two cycles can never be created from one decision. The use case refuses a
     * second write on its own as well — this only keeps the UI honest about it.
     */
    private fun startNextCycle() {
        val local = state.value
        if (local.status == CycleReviewStatus.Saving) return
        val decision = local.decision() ?: return

        state.update { it.copy(status = CycleReviewStatus.Saving) }
        viewModelScope.launch {
            startNextTrainingCycle(decision)
                .onSuccess {
                    state.update { it.copy(status = CycleReviewStatus.Completed) }
                    navigationChannel.send(CycleReviewNavigation.NextCycleStarted)
                }
                .onFailure { error ->
                    state.update {
                        it.copy(
                            status = CycleReviewStatus.Failed(
                                error.message ?: "Could not start the next cycle.",
                            ),
                        )
                    }
                }
        }
    }

    private data class LocalState(
        val isLoading: Boolean = true,
        val review: CycleReview? = null,
        val choices: Map<ExerciseCategory, ProgressionChoice> = ExerciseCategory.bigThree
            .associateWith { ProgressionChoice.INCREASE },
        val customInputs: Map<ExerciseCategory, String> = emptyMap(),
        val status: CycleReviewStatus = CycleReviewStatus.Idle,
        val error: String? = null,
    ) {
        fun withReview(review: CycleReview) = copy(isLoading = false, review = review, error = null)

        /** The change one lift is set to, or null while a typed value is not yet a number. */
        fun changeFor(category: ExerciseCategory): ReferenceMaxChange? =
            when (choices[category] ?: ProgressionChoice.INCREASE) {
                ProgressionChoice.INCREASE -> DefaultCycleProgressionPolicy.defaultChange(category)
                ProgressionChoice.KEEP -> ReferenceMaxChange.Keep
                ProgressionChoice.CUSTOM -> ReferenceMaxDelta
                    .parse(customInputs[category].orEmpty())
                    ?.let(ReferenceMaxChange::Custom)
            }

        /**
         * Where one lift would land, or null if it would not land anywhere trainable.
         *
         * The domain owns that rule, so the screen asks it rather than re-deciding what
         * "valid" means — and asks it once, for both the preview and the button.
         */
        fun progressionFor(category: ExerciseCategory, current: Weight): LiftProgression? {
            val change = changeFor(category) ?: return null
            return runCatching { LiftProgression(category, current, change) }.getOrNull()
        }

        /**
         * Null unless all three lifts are decided and trainable — exactly the rule the button
         * is enabled by, so an event that slips past a disabled button writes nothing.
         */
        fun decision(): CycleProgressionDecision? {
            val maxes = review?.currentReferenceMaxes ?: return null
            val squat = progressionFor(ExerciseCategory.SQUAT, maxes.squat) ?: return null
            val benchPress = progressionFor(ExerciseCategory.BENCH_PRESS, maxes.benchPress)
                ?: return null
            val deadlift = progressionFor(ExerciseCategory.DEADLIFT, maxes.deadlift) ?: return null
            return CycleProgressionDecision(squat.change, benchPress.change, deadlift.change)
        }

        fun toUiState(): CycleReviewUiState {
            val review = review ?: return CycleReviewUiState(isLoading = isLoading, error = error)
            return CycleReviewUiState(
                isLoading = false,
                summary = review.toSummary(),
                lifts = review.currentReferenceMaxes.toLiftUiStates(),
                nextCycleLabel = "CYCLE ${review.nextCycleNumber}",
                status = status,
                error = error,
            )
        }

        private fun CycleReview.toSummary() = CycleReviewSummary(
            cycleLabel = summary.cycle.label,
            weeksLabel = "${summary.weekCount} WEEKS",
            workoutsLabel = "${summary.completedWorkouts}/${summary.plannedWorkouts}",
        )

        private fun ReferenceMaxSnapshot.toLiftUiStates(): List<LiftProgressionUiState> =
            byCategory.map { (category, current) ->
                val next = progressionFor(category, current)?.next
                LiftProgressionUiState(
                    category = category,
                    label = Format.categoryLabel(category),
                    code = Format.categoryShort(category),
                    current = current.format(),
                    choice = choices[category] ?: ProgressionChoice.INCREASE,
                    increaseLabel = "${DefaultCycleProgressionPolicy.defaultIncrease(category)} KG",
                    customInput = customInputs[category].orEmpty(),
                    next = next?.format(),
                    error = when {
                        next != null -> null
                        // Nothing typed yet is a prompt; something impossible is a refusal.
                        changeFor(category) == null -> "Enter a change, for example 5 or -2.5"
                        else -> "That leaves nothing to train from"
                    },
                )
            }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
