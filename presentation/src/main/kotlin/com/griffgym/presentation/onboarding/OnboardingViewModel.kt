package com.griffgym.presentation.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griffgym.application.metrics.CalculateEstimated1RmUseCase
import com.griffgym.application.onboarding.CompleteOnboardingUseCase
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.Weight
import com.griffgym.presentation.format.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the whole of first-run setup: one screen per lift, then the summary.
 *
 * A single ViewModel owns all three lifts rather than one per step, because the steps are
 * not independent — going back has to show what was already confirmed, the summary edits
 * the same values, and the program can only be generated from all three at once.
 *
 * Confirmed maxes are mirrored into [SavedStateHandle], so a lifter who is interrupted
 * mid-setup and comes back to a restarted process does not have to re-enter what they
 * already worked out. In-progress typing is intentionally not persisted that far: it
 * survives configuration changes with the ViewModel, and restoring a half-typed field on
 * top of a confirmed value would be more confusing than helpful.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val calculateEstimated1Rm: CalculateEstimated1RmUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(
        FormState(
            forms = OnboardingLifts.associateWith { category ->
                LiftForm(confirmedInput = savedStateHandle[confirmedKey(category)] ?: "")
            },
        ),
    )

    /** Held so a second tap on "BUILD MY PROGRAM" cannot start a second generation. */
    private var buildJob: Job? = null

    val uiState: StateFlow<OnboardingUiState> = state
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = state.value.toUiState(),
        )

    fun onEvent(event: OnboardingUiEvent) {
        when (event) {
            is OnboardingUiEvent.ModeChanged ->
                updateForm(event.category) { it.copy(mode = event.mode) }

            is OnboardingUiEvent.WeightChanged ->
                updateForm(event.category) { it.copy(weightInput = event.value) }

            is OnboardingUiEvent.RepsChanged ->
                updateForm(event.category) { it.copy(reps = event.value) }

            is OnboardingUiEvent.OneRepMaxChanged ->
                updateForm(event.category) { it.copy(oneRepMaxInput = event.value) }

            is OnboardingUiEvent.Confirm -> confirm(event.category)

            is OnboardingUiEvent.SummaryValueChanged ->
                updateForm(event.category) { it.copy(confirmedInput = event.value) }

            OnboardingUiEvent.Build -> build()

            OnboardingUiEvent.DismissError ->
                state.update { it.copy(status = OnboardingStatus.Idle) }
        }
    }

    private fun confirm(category: ExerciseCategory) {
        val pending = state.value.forms[category]?.pendingOneRepMax(calculateEstimated1Rm) ?: return
        updateForm(category) { it.copy(confirmedInput = pending.format()) }
    }

    private fun build() {
        // Two independent guards: the button is disabled while building, and a job that is
        // still running short circuits anything that slipped past it.
        if (buildJob?.isActive == true) return
        val maxes = state.value.confirmedMaxes()
        if (maxes.size != OnboardingLifts.size) return

        state.update { it.copy(status = OnboardingStatus.Building) }
        buildJob = viewModelScope.launch {
            val result = completeOnboarding(maxes)
            state.update {
                it.copy(
                    status = result.fold(
                        onSuccess = { OnboardingStatus.Completed },
                        onFailure = { OnboardingStatus.Failed(BUILD_FAILED_MESSAGE) },
                    ),
                )
            }
        }
    }

    private fun updateForm(category: ExerciseCategory, transform: (LiftForm) -> LiftForm) {
        state.update { current ->
            val form = current.forms[category] ?: return@update current
            val updated = transform(form)
            savedStateHandle[confirmedKey(category)] = updated.confirmedInput
            current.copy(forms = current.forms + (category to updated))
        }
    }

    private fun FormState.toUiState(): OnboardingUiState {
        val steps = OnboardingLifts.mapIndexed { index, category ->
            val form = forms.getValue(category)
            val pending = form.pendingOneRepMax(calculateEstimated1Rm)
            LiftStepUiState(
                category = category,
                label = Format.categoryLabel(category),
                stepNumber = index + 1,
                stepCount = OnboardingLifts.size,
                mode = form.mode,
                weightInput = form.weightInput,
                reps = form.reps,
                oneRepMaxInput = form.oneRepMaxInput,
                pendingOneRepMax = pending?.format(),
                isEstimateReliable = form.isEstimateReliable(calculateEstimated1Rm),
                confirmedOneRepMax = form.confirmed()?.format(),
                error = form.hint(pending),
            )
        }

        return OnboardingUiState(
            steps = steps,
            summary = OnboardingSummaryUiState(
                lifts = OnboardingLifts.map { category ->
                    val form = forms.getValue(category)
                    SummaryLiftUiState(
                        category = category,
                        label = Format.categoryLabel(category),
                        input = form.confirmedInput,
                        error = if (form.confirmed() == null) INVALID_MAX_MESSAGE else null,
                    )
                },
                isBuilding = status == OnboardingStatus.Building,
                error = (status as? OnboardingStatus.Failed)?.message,
            ),
            status = status,
        )
    }

    private fun FormState.confirmedMaxes(): Map<ExerciseCategory, Weight> =
        forms.mapNotNull { (category, form) -> form.confirmed()?.let { category to it } }.toMap()

    private data class FormState(
        val forms: Map<ExerciseCategory, LiftForm>,
        val status: OnboardingStatus = OnboardingStatus.Idle,
    )

    private data class LiftForm(
        val mode: OneRepMaxEntryMode = OneRepMaxEntryMode.CALCULATOR,
        val weightInput: String = "",
        val reps: Int = DEFAULT_REPS,
        val oneRepMaxInput: String = "",
        /** The confirmed max as raw text: what the summary shows and lets the lifter edit. */
        val confirmedInput: String = "",
    ) {
        fun confirmed(): Weight? = confirmedInput.toPositiveWeight()

        /** What the confirm button would store, or null while the input cannot produce one. */
        fun pendingOneRepMax(calculate: CalculateEstimated1RmUseCase): Weight? = when (mode) {
            OneRepMaxEntryMode.CALCULATOR ->
                Weight.parse(weightInput)
                    ?.let { calculate(it, reps) }
                    ?.weight
                    ?.takeIf { !it.isZero }

            OneRepMaxEntryMode.DIRECT -> oneRepMaxInput.toPositiveWeight()
        }

        fun isEstimateReliable(calculate: CalculateEstimated1RmUseCase): Boolean = when (mode) {
            OneRepMaxEntryMode.CALCULATOR ->
                Weight.parse(weightInput)?.let { calculate(it, reps) }?.isReliable != false

            OneRepMaxEntryMode.DIRECT -> true
        }

        /**
         * Only complains once there is something to complain about — an untouched field is
         * not a mistake, it is a field the lifter has not reached yet.
         */
        fun hint(pending: Weight?): String? {
            if (pending != null) return null
            val raw = if (mode == OneRepMaxEntryMode.CALCULATOR) weightInput else oneRepMaxInput
            return if (raw.isBlank()) null else INVALID_MAX_MESSAGE
        }

        private fun String.toPositiveWeight(): Weight? = Weight.parse(this)?.takeIf { !it.isZero }
    }

    private companion object {
        const val DEFAULT_REPS = 5
        const val STOP_TIMEOUT_MS = 5_000L
        const val INVALID_MAX_MESSAGE = "Enter a weight above 0"
        const val BUILD_FAILED_MESSAGE = "Could not build your program. Try again."

        fun confirmedKey(category: ExerciseCategory) = "onboarding_confirmed_${category.name}"
    }
}
