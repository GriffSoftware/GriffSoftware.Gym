package com.griffgym.presentation.onboarding

import androidx.lifecycle.SavedStateHandle
import com.griffgym.application.cycle.StartTrainingCycleUseCase
import com.griffgym.application.metrics.CalculateEstimated1RmUseCase
import com.griffgym.application.onboarding.CompleteOnboardingUseCase
import com.griffgym.application.onboarding.GenerateTrainingBlockUseCase
import com.griffgym.domain.model.CycleStatus
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.GeneratedProgram
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.TrainingCycle
import com.griffgym.domain.model.TrainingCycleSummary
import com.griffgym.domain.model.TrainingProgram
import com.griffgym.domain.model.Weight
import com.griffgym.domain.repository.OnboardingRepository
import com.griffgym.domain.repository.TrainingCycleRepository
import com.griffgym.domain.repository.TrainingProgramRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The setup flow's state machine: what the buttons are allowed to do, and what happens when
 * the lifter taps the one that writes their block twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    /**
     * Unconfined because `uiState` is shared on `viewModelScope`, which runs on Main: a
     * queueing dispatcher would leave the state at its initial value until advanced, which
     * is not how the flow behaves in front of a screen. Work that genuinely suspends —
     * building the program — still has to be advanced explicitly.
     */
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-03-04T09:30:00Z"), ZoneOffset.UTC)

    private lateinit var cycles: RecordingCycleRepository
    private lateinit var programs: RecordingProgramRepository
    private lateinit var onboarding: RecordingOnboardingRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing can be built until all three lifts are confirmed`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collectUiState(viewModel)

        assertFalse(viewModel.uiState.value.summary.canBuild)

        confirmAll(viewModel)

        assertTrue(viewModel.uiState.value.summary.canBuild)
    }

    @Test
    fun `the calculator turns a hard set into an estimate the lifter can accept`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            collectUiState(viewModel)

            viewModel.onEvent(OnboardingUiEvent.WeightChanged(ExerciseCategory.SQUAT, "160"))
            viewModel.onEvent(OnboardingUiEvent.RepsChanged(ExerciseCategory.SQUAT, 5))

            val step = viewModel.uiState.value.steps.first()
            // Epley: 160 x (1 + 5/30) = 186.67 kg.
            assertEquals("186.67", step.pendingOneRepMax)
            assertTrue(step.canConfirm)
            assertNull(step.confirmedOneRepMax)

            viewModel.onEvent(OnboardingUiEvent.Confirm(ExerciseCategory.SQUAT))

            assertEquals("186.67", viewModel.uiState.value.steps.first().confirmedOneRepMax)
        }

    @Test
    fun `a comma is as good as a dot`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collectUiState(viewModel)

        viewModel.onEvent(OnboardingUiEvent.ModeChanged(ExerciseCategory.SQUAT, OneRepMaxEntryMode.DIRECT))
        viewModel.onEvent(OnboardingUiEvent.OneRepMaxChanged(ExerciseCategory.SQUAT, "212,5"))

        assertEquals("212.5", viewModel.uiState.value.steps.first().pendingOneRepMax)
    }

    @Test
    fun `zero, empty and nonsense are all refused`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collectUiState(viewModel)
        viewModel.onEvent(OnboardingUiEvent.ModeChanged(ExerciseCategory.SQUAT, OneRepMaxEntryMode.DIRECT))

        listOf("", "0", "0.0", "abc", "-5").forEach { input ->
            viewModel.onEvent(OnboardingUiEvent.OneRepMaxChanged(ExerciseCategory.SQUAT, input))
            val step = viewModel.uiState.value.steps.first()
            assertFalse("'$input' should not be confirmable", step.canConfirm)
        }

        // An untouched field is not an error; a wrong one is.
        viewModel.onEvent(OnboardingUiEvent.OneRepMaxChanged(ExerciseCategory.SQUAT, ""))
        assertNull(viewModel.uiState.value.steps.first().error)
        viewModel.onEvent(OnboardingUiEvent.OneRepMaxChanged(ExerciseCategory.SQUAT, "0"))
        assertEquals("Enter a weight above 0", viewModel.uiState.value.steps.first().error)
    }

    @Test
    fun `editing a max on the summary invalidates the build until it is valid again`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            collectUiState(viewModel)
            confirmAll(viewModel)

            viewModel.onEvent(
                OnboardingUiEvent.SummaryValueChanged(ExerciseCategory.DEADLIFT, ""),
            )
            assertFalse(viewModel.uiState.value.summary.canBuild)

            viewModel.onEvent(
                OnboardingUiEvent.SummaryValueChanged(ExerciseCategory.DEADLIFT, "227.5"),
            )
            assertTrue(viewModel.uiState.value.summary.canBuild)

            viewModel.onEvent(OnboardingUiEvent.Build)
            advanceUntilIdle()

            assertEquals(
                Weight.of(227.5),
                cycles.savedMaxes[ExerciseCategory.DEADLIFT],
            )
        }

    @Test
    fun `building the program stores the maxes and finishes setup`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collectUiState(viewModel)
        confirmAll(viewModel)

        viewModel.onEvent(OnboardingUiEvent.Build)
        advanceUntilIdle()

        assertEquals(OnboardingStatus.Completed, viewModel.uiState.value.status)
        assertEquals(1, cycles.started.size)
        assertTrue(onboarding.completed)
    }

    @Test
    fun `a double tap can only ever build one program`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val viewModel = viewModel(gate = gate)
        collectUiState(viewModel)
        confirmAll(viewModel)

        viewModel.onEvent(OnboardingUiEvent.Build)
        yield()
        assertTrue(viewModel.uiState.value.summary.isBuilding)
        // The button is disabled by now, but an event that slipped through must not write twice.
        viewModel.onEvent(OnboardingUiEvent.Build)
        viewModel.onEvent(OnboardingUiEvent.Build)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, cycles.started.size)
        assertEquals(OnboardingStatus.Completed, viewModel.uiState.value.status)
    }

    @Test
    fun `a failed build can be dismissed and tried again`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collectUiState(viewModel)
        confirmAll(viewModel)
        cycles.failOnStart = true

        viewModel.onEvent(OnboardingUiEvent.Build)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.status is OnboardingStatus.Failed)
        assertFalse(onboarding.completed)
        assertEquals(
            "Could not build your program. Try again.",
            viewModel.uiState.value.summary.error,
        )
        // The summary is still usable, so retrying is one tap and not a restart.
        assertTrue(viewModel.uiState.value.summary.canBuild)

        cycles.failOnStart = false
        viewModel.onEvent(OnboardingUiEvent.Build)
        advanceUntilIdle()

        assertEquals(OnboardingStatus.Completed, viewModel.uiState.value.status)
    }

    @Test
    fun `confirmed maxes survive the process being restarted`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = viewModel(handle = handle)
        collectUiState(first)
        confirmAll(first)

        // A new ViewModel over the same saved state is what process death looks like.
        val restored = viewModel(handle = handle)
        collectUiState(restored)

        assertEquals(
            listOf("210", "170", "225"),
            restored.uiState.value.summary.lifts.map { it.input },
        )
        assertTrue(restored.uiState.value.summary.canBuild)
    }

    private fun confirmAll(viewModel: OnboardingViewModel) {
        mapOf(
            ExerciseCategory.SQUAT to "210",
            ExerciseCategory.BENCH_PRESS to "170",
            ExerciseCategory.DEADLIFT to "225",
        ).forEach { (category, value) ->
            viewModel.onEvent(OnboardingUiEvent.ModeChanged(category, OneRepMaxEntryMode.DIRECT))
            viewModel.onEvent(OnboardingUiEvent.OneRepMaxChanged(category, value))
            viewModel.onEvent(OnboardingUiEvent.Confirm(category))
        }
    }

    /** `uiState` only produces while collected, so a test has to stand in for the screen. */
    private fun kotlinx.coroutines.test.TestScope.collectUiState(viewModel: OnboardingViewModel) {
        backgroundScope.launch { viewModel.uiState.collect { } }
    }

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        gate: CompletableDeferred<Unit>? = null,
    ): OnboardingViewModel {
        cycles = RecordingCycleRepository(gate)
        programs = RecordingProgramRepository(cycles)
        onboarding = RecordingOnboardingRepository()
        return OnboardingViewModel(
            savedStateHandle = handle,
            calculateEstimated1Rm = CalculateEstimated1RmUseCase(),
            completeOnboarding = CompleteOnboardingUseCase(
                startTrainingCycle = StartTrainingCycleUseCase(
                    generateTrainingBlock = GenerateTrainingBlockUseCase(),
                    cycleRepository = cycles,
                    programRepository = programs,
                    clock = clock,
                ),
                trainingProgramRepository = programs,
                onboardingRepository = onboarding,
            ),
        )
    }

    /**
     * Records the cycle setup starts: the plan and the maxes it was written with, which are
     * persisted as one call. [gate] lets a test hold the write open and race a second tap
     * against it.
     */
    private class RecordingCycleRepository(
        private val gate: CompletableDeferred<Unit>?,
    ) : TrainingCycleRepository by UnusedCycleRepository {

        val started = mutableListOf<GeneratedProgram>()
        val savedMaxes = mutableMapOf<ExerciseCategory, Weight>()
        var failOnStart = false

        override suspend fun getCurrentCycle(): TrainingCycle? = null

        override suspend fun startCycle(
            program: GeneratedProgram,
            referenceMaxes: ReferenceMaxSnapshot,
            date: LocalDate,
            startedAt: Instant,
        ): TrainingCycle {
            gate?.await()
            if (failOnStart) throw IllegalStateException("disk full")
            started += program
            savedMaxes += referenceMaxes.byCategory
            return TrainingCycle(
                id = started.size.toLong(),
                cycleNumber = started.size,
                status = CycleStatus.ACTIVE,
                startedAt = startedAt,
                completedAt = null,
                referenceMaxes = referenceMaxes,
                createdAt = startedAt,
            )
        }
    }

    /** Setup never replaces an existing plan, so the flag is all this has to answer. */
    private class RecordingProgramRepository(
        private val cycles: RecordingCycleRepository,
    ) : TrainingProgramRepository by UnusedProgramRepository {
        override suspend fun hasProgram(): Boolean = cycles.started.isNotEmpty()
    }

    private class RecordingOnboardingRepository : OnboardingRepository {
        var completed = false
        override suspend fun isOnboardingCompleted(): Boolean = completed
        override suspend fun markOnboardingCompleted() {
            completed = true
        }
    }

    /** Everything setup never calls. Delegating keeps the test double to what it tests. */
    private object UnusedProgramRepository : TrainingProgramRepository {
        override fun observeActiveProgram() = MutableStateFlow(null)
        override suspend fun getActiveProgram() = null
        override suspend fun hasProgram() = false
        override fun observeCurrentWorkoutTemplate() = MutableStateFlow(null)
        override suspend fun getCurrentWorkoutTemplate() = null
        override fun observeCurrentTrainingWeek() = MutableStateFlow(null)
        override suspend fun getWorkoutTemplate(id: Long) = null
        override suspend fun getWorkoutTemplateAfter(sequenceNumber: Int) = null
        override suspend fun setCurrentWorkoutTemplate(templateId: Long?) = Unit
    }

    private object UnusedCycleRepository : TrainingCycleRepository {
        override fun observeCurrentCycle() = MutableStateFlow(null)
        override suspend fun getCurrentCycle(): TrainingCycle? = null
        override suspend fun getCycle(id: Long): TrainingCycle? = null
        override fun observeCycleSummaries() = MutableStateFlow(emptyList<TrainingCycleSummary>())
        override suspend fun getCycleSummary(cycleId: Long): TrainingCycleSummary? = null
        override suspend fun getCycleProgram(cycleId: Long): TrainingProgram? = null
        override suspend fun startCycle(
            program: GeneratedProgram,
            referenceMaxes: ReferenceMaxSnapshot,
            date: LocalDate,
            startedAt: Instant,
        ): TrainingCycle = throw UnsupportedOperationException()

        override suspend fun completeCurrentCycle(completedAt: Instant): TrainingCycle? =
            throw UnsupportedOperationException()
    }
}
