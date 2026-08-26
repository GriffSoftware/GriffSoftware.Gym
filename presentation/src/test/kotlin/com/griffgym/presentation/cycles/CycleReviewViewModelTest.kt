package com.griffgym.presentation.cycles

import com.griffgym.application.cycle.CalculateNextCycleReferenceMaxUseCase
import com.griffgym.application.cycle.GetCurrentReferenceMaxSnapshotUseCase
import com.griffgym.application.cycle.GetCycleReviewUseCase
import com.griffgym.application.cycle.StartNextTrainingCycleUseCase
import com.griffgym.application.cycle.StartTrainingCycleUseCase
import com.griffgym.application.onboarding.GenerateTrainingBlockUseCase
import com.griffgym.domain.model.CycleStatus
import com.griffgym.domain.model.CycleWeekProgress
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.GeneratedProgram
import com.griffgym.domain.model.ReferenceMax
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.TrainingCycle
import com.griffgym.domain.model.TrainingCycleSummary
import com.griffgym.domain.model.TrainingProgram
import com.griffgym.domain.model.Weight
import com.griffgym.domain.model.WorkoutTemplate
import com.griffgym.domain.repository.ReferenceMaxRepository
import com.griffgym.domain.repository.TrainingCycleRepository
import com.griffgym.domain.repository.TrainingProgramRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
 * The end-of-cycle decision as the screen actually behaves: what the NEXT number says while
 * the lifter is choosing, what the button is allowed to do, and what a second tap costs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CycleReviewViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-04-01T09:00:00Z"), ZoneOffset.UTC)

    private lateinit var cycles: RecordingCycleRepository
    private lateinit var programs: StubProgramRepository
    private lateinit var referenceMaxes: StubReferenceMaxRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the review opens on the defaults, every lift stepped up`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collectUiState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("CYCLE 3", state.summary!!.cycleLabel)
        assertEquals("6 WEEKS", state.summary!!.weeksLabel)
        assertEquals("18/18", state.summary!!.workoutsLabel)
        assertEquals("CYCLE 4", state.nextCycleLabel)

        // Squat, deadlift, bench: the order every list of lifts in the app is drawn in.
        assertEquals(
            listOf(
                ExerciseCategory.SQUAT,
                ExerciseCategory.DEADLIFT,
                ExerciseCategory.BENCH_PRESS,
            ),
            state.lifts.map { it.category },
        )
        assertEquals(
            listOf("+5 KG", "+5 KG", "+2.5 KG"),
            state.lifts.map { it.increaseLabel },
        )
        assertEquals(listOf("205", "225", "152.5"), state.lifts.map { it.next })
        assertTrue(state.lifts.all { it.choice == ProgressionChoice.INCREASE })
        assertTrue(state.canStartNextCycle)
    }

    @Test
    fun `keeping a lift leaves its next max where it is`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collectUiState(viewModel)
        advanceUntilIdle()

        viewModel.onEvent(
            CycleReviewUiEvent.ChoiceSelected(ExerciseCategory.BENCH_PRESS, ProgressionChoice.KEEP),
        )

        assertEquals(listOf("205", "225", "150"), viewModel.uiState.value.lifts.map { it.next })
        assertTrue(viewModel.uiState.value.canStartNextCycle)
    }

    @Test
    fun `a custom decrease is accepted and shows where the lift lands`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collectUiState(viewModel)
        advanceUntilIdle()

        viewModel.onEvent(
            CycleReviewUiEvent.ChoiceSelected(ExerciseCategory.DEADLIFT, ProgressionChoice.CUSTOM),
        )
        viewModel.onEvent(CycleReviewUiEvent.CustomDeltaChanged(ExerciseCategory.DEADLIFT, "-7,5"))

        val deadlift = viewModel.uiState.value.lifts
            .single { it.category == ExerciseCategory.DEADLIFT }
        assertEquals("212.5", deadlift.next)
        assertNull(deadlift.error)
        assertTrue(viewModel.uiState.value.canStartNextCycle)
    }

    @Test
    fun `a half-typed custom change blocks the button without shouting`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collectUiState(viewModel)
        advanceUntilIdle()

        viewModel.onEvent(
            CycleReviewUiEvent.ChoiceSelected(ExerciseCategory.SQUAT, ProgressionChoice.CUSTOM),
        )
        viewModel.onEvent(CycleReviewUiEvent.CustomDeltaChanged(ExerciseCategory.SQUAT, "-"))

        val squat = viewModel.uiState.value.lifts
            .single { it.category == ExerciseCategory.SQUAT }
        assertNull(squat.next)
        assertEquals("Enter a change, for example 5 or -2.5", squat.error)
        assertFalse(viewModel.uiState.value.canStartNextCycle)
    }

    @Test
    fun `a change that would leave nothing to train from is refused`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collectUiState(viewModel)
        advanceUntilIdle()

        viewModel.onEvent(
            CycleReviewUiEvent.ChoiceSelected(ExerciseCategory.SQUAT, ProgressionChoice.CUSTOM),
        )
        viewModel.onEvent(CycleReviewUiEvent.CustomDeltaChanged(ExerciseCategory.SQUAT, "-200"))

        assertEquals(
            "That leaves nothing to train from",
            viewModel.uiState.value.lifts.single { it.category == ExerciseCategory.SQUAT }.error,
        )
        assertFalse(viewModel.uiState.value.canStartNextCycle)

        viewModel.onEvent(CycleReviewUiEvent.StartNextCycle)
        advanceUntilIdle()

        // Nothing was written, and the screen stays where the lifter left it.
        assertTrue(cycles.started.isEmpty())
        assertEquals(CycleReviewStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `starting the next cycle writes it once and leaves the screen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collectUiState(viewModel)
        advanceUntilIdle()
        val navigation = mutableListOf<CycleReviewNavigation>()
        backgroundScope.launch { viewModel.navigation.collect { navigation += it } }

        viewModel.onEvent(CycleReviewUiEvent.StartNextCycle)
        advanceUntilIdle()

        assertEquals(1, cycles.started.size)
        assertEquals(
            ReferenceMaxSnapshot(Weight.of(205.0), Weight.of(152.5), Weight.of(225.0)),
            cycles.startedWith.single(),
        )
        assertEquals(CycleReviewStatus.Completed, viewModel.uiState.value.status)
        assertEquals(listOf(CycleReviewNavigation.NextCycleStarted), navigation)
    }

    @Test
    fun `a double tap on start can only ever create one cycle`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val viewModel = viewModel(gate = gate)
        collectUiState(viewModel)
        advanceUntilIdle()

        viewModel.onEvent(CycleReviewUiEvent.StartNextCycle)
        yield()
        assertTrue(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.canStartNextCycle)
        // The button is disabled by now, but an event that slipped through must not write twice.
        viewModel.onEvent(CycleReviewUiEvent.StartNextCycle)
        viewModel.onEvent(CycleReviewUiEvent.StartNextCycle)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, cycles.started.size)
        assertEquals(CycleReviewStatus.Completed, viewModel.uiState.value.status)
    }

    @Test
    fun `a failed write can be dismissed and tried again`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collectUiState(viewModel)
        advanceUntilIdle()
        cycles.failOnStart = true

        viewModel.onEvent(CycleReviewUiEvent.StartNextCycle)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.status is CycleReviewStatus.Failed)
        assertTrue(cycles.started.isEmpty())
        // The decision survives the failure, so retrying is one tap and not a restart.
        assertTrue(viewModel.uiState.value.canStartNextCycle)

        cycles.failOnStart = false
        viewModel.onEvent(CycleReviewUiEvent.DismissError)
        viewModel.onEvent(CycleReviewUiEvent.StartNextCycle)
        advanceUntilIdle()

        assertEquals(CycleReviewStatus.Completed, viewModel.uiState.value.status)
        assertEquals(1, cycles.started.size)
    }

    @Test
    fun `with no cycle to review the screen says so instead of inventing numbers`() =
        runTest(dispatcher) {
            val viewModel = viewModel(cycle = null)
            collectUiState(viewModel)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.summary)
            assertTrue(state.lifts.isEmpty())
            assertFalse(state.canStartNextCycle)
            assertEquals("There is no cycle to review", state.error)
        }

    /** `uiState` only produces while collected, so a test has to stand in for the screen. */
    private fun TestScope.collectUiState(viewModel: CycleReviewViewModel) {
        backgroundScope.launch { viewModel.uiState.collect { } }
    }

    private fun viewModel(
        cycle: TrainingCycle? = COMPLETED_CYCLE,
        gate: CompletableDeferred<Unit>? = null,
    ): CycleReviewViewModel {
        cycles = RecordingCycleRepository(cycle, gate)
        programs = StubProgramRepository()
        referenceMaxes = StubReferenceMaxRepository()

        val currentMaxes = GetCurrentReferenceMaxSnapshotUseCase(referenceMaxes, cycles)
        return CycleReviewViewModel(
            getCycleReview = GetCycleReviewUseCase(cycles, currentMaxes),
            startNextTrainingCycle = StartNextTrainingCycleUseCase(
                getCurrentReferenceMaxes = currentMaxes,
                calculateNextReferenceMaxes = CalculateNextCycleReferenceMaxUseCase(),
                startTrainingCycle = StartTrainingCycleUseCase(
                    generateTrainingBlock = GenerateTrainingBlockUseCase(),
                    cycleRepository = cycles,
                    programRepository = programs,
                    clock = clock,
                ),
            ),
        )
    }

    private class RecordingCycleRepository(
        private val cycle: TrainingCycle?,
        private val gate: CompletableDeferred<Unit>?,
    ) : TrainingCycleRepository {

        val started = mutableListOf<GeneratedProgram>()
        val startedWith = mutableListOf<ReferenceMaxSnapshot>()
        var failOnStart = false

        override fun observeCurrentCycle(): Flow<TrainingCycle?> = MutableStateFlow(cycle)
        override suspend fun getCurrentCycle(): TrainingCycle? = cycle
        override suspend fun getCycle(id: Long): TrainingCycle? = cycle?.takeIf { it.id == id }

        override fun observeCycleSummaries(): Flow<List<TrainingCycleSummary>> =
            MutableStateFlow(listOfNotNull(cycle?.let(::summaryOf)))

        override suspend fun getCycleSummary(cycleId: Long): TrainingCycleSummary? =
            cycle?.takeIf { it.id == cycleId }?.let(::summaryOf)

        override suspend fun getCycleProgram(cycleId: Long): TrainingProgram? = null

        override suspend fun startCycle(
            program: GeneratedProgram,
            referenceMaxes: ReferenceMaxSnapshot,
            date: LocalDate,
            startedAt: Instant,
        ): TrainingCycle {
            gate?.await()
            if (failOnStart) throw IllegalStateException("disk full")
            started += program
            startedWith += referenceMaxes
            return TrainingCycle(
                id = 99,
                cycleNumber = (cycle?.cycleNumber ?: 0) + 1,
                status = CycleStatus.ACTIVE,
                startedAt = startedAt,
                completedAt = null,
                referenceMaxes = referenceMaxes,
                createdAt = startedAt,
            )
        }

        override suspend fun completeCurrentCycle(completedAt: Instant): TrainingCycle? = cycle

        /** A cycle trained to the end: six weeks, three days each, all of them logged. */
        private fun summaryOf(cycle: TrainingCycle) = TrainingCycleSummary(
            cycle = cycle,
            weeks = (1..6).map { week ->
                CycleWeekProgress(
                    weekNumber = week,
                    label = if (week == 6) "DELOAD" else "W$week",
                    isDeload = week == 6,
                    plannedWorkouts = 3,
                    completedWorkouts = 3,
                )
            },
        )
    }

    /** The block has run out, which is the only reason the review is on screen at all. */
    private class StubProgramRepository : TrainingProgramRepository {
        override fun observeActiveProgram(): Flow<TrainingProgram?> = MutableStateFlow(null)
        override suspend fun getActiveProgram(): TrainingProgram? = null
        override suspend fun hasProgram(): Boolean = true
        override fun observeCurrentWorkoutTemplate(): Flow<WorkoutTemplate?> = MutableStateFlow(null)
        override suspend fun getCurrentWorkoutTemplate(): WorkoutTemplate? = null
        override fun observeCurrentTrainingWeek() = MutableStateFlow(null)
        override suspend fun getWorkoutTemplate(id: Long): WorkoutTemplate? = null
        override suspend fun getWorkoutTemplateAfter(sequenceNumber: Int): WorkoutTemplate? = null
        override suspend fun setCurrentWorkoutTemplate(templateId: Long?) = Unit
    }

    private class StubReferenceMaxRepository : ReferenceMaxRepository {
        private val state = MutableStateFlow(
            listOf(
                ReferenceMax(ExerciseCategory.SQUAT, Weight.of(200.0), TODAY),
                ReferenceMax(ExerciseCategory.BENCH_PRESS, Weight.of(150.0), TODAY),
                ReferenceMax(ExerciseCategory.DEADLIFT, Weight.of(220.0), TODAY),
            ),
        )

        override fun observeReferenceMaxes(): Flow<List<ReferenceMax>> = state

        override suspend fun getReferenceMax(category: ExerciseCategory): ReferenceMax? =
            state.value.firstOrNull { it.category == category }

        override suspend fun updateReferenceMax(
            category: ExerciseCategory,
            weight: Weight,
            updatedOn: LocalDate,
        ) = Unit

        override suspend fun hasAnyReferenceMax(): Boolean = true
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 4, 1)

        val COMPLETED_CYCLE = TrainingCycle(
            id = 3,
            cycleNumber = 3,
            status = CycleStatus.COMPLETED,
            startedAt = Instant.parse("2026-02-16T09:00:00Z"),
            completedAt = Instant.parse("2026-03-30T19:00:00Z"),
            referenceMaxes = ReferenceMaxSnapshot(
                squat = Weight.of(200.0),
                benchPress = Weight.of(150.0),
                deadlift = Weight.of(220.0),
            ),
            createdAt = Instant.parse("2026-02-16T09:00:00Z"),
        )
    }
}
