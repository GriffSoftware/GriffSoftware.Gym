package com.griffgym.application

import com.griffgym.domain.model.CycleStatus
import com.griffgym.domain.model.CycleWeekProgress
import com.griffgym.domain.model.Exercise
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseLog
import com.griffgym.domain.model.ExerciseTemplate
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.GeneratedProgram
import com.griffgym.domain.model.PlannedSet
import com.griffgym.domain.model.ReferenceMax
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.RpeTarget
import com.griffgym.domain.model.SetLog
import com.griffgym.domain.model.SetResult
import com.griffgym.domain.model.TrainingCycle
import com.griffgym.domain.model.TrainingCycleSummary
import com.griffgym.domain.model.TrainingProgram
import com.griffgym.domain.model.TrainingVolume
import com.griffgym.domain.model.TrainingWeek
import com.griffgym.domain.model.Weight
import com.griffgym.domain.model.WorkoutSession
import com.griffgym.domain.model.WorkoutStatus
import com.griffgym.domain.model.WorkoutTemplate
import com.griffgym.domain.repository.ExerciseRepository
import com.griffgym.domain.repository.OnboardingRepository
import com.griffgym.domain.repository.ReferenceMaxRepository
import com.griffgym.domain.repository.TrainingCycleRepository
import com.griffgym.domain.repository.TrainingProgramRepository
import com.griffgym.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

val squat = Exercise(1, "Przysiad", ExerciseCategory.SQUAT)
val deadlift = Exercise(2, "Martwy ciąg", ExerciseCategory.DEADLIFT)
val bench = Exercise(3, "Ławka", ExerciseCategory.BENCH_PRESS)
val accessory = Exercise(4, "Skos Smith", ExerciseCategory.ACCESSORY)

/**
 * A stand-in plan. The cycle fake only records what it is handed — generating a real block
 * is [com.griffgym.application.onboarding.GenerateTrainingBlockUseCase]'s job, and it has
 * tests of its own.
 */
fun generatedProgram(name: String = "Blok IV — Siła"): GeneratedProgram =
    GeneratedProgram(name = name, weeks = emptyList())

fun setLog(
    id: Long,
    position: Int = 1,
    weight: Double? = 100.0,
    reps: Int? = 5,
    completed: Boolean = true,
    plannedWeight: Double? = weight,
    plannedReps: Int? = reps,
) = SetLog(
    id = id,
    position = position,
    plannedWeight = plannedWeight?.let(Weight::of),
    plannedReps = plannedReps,
    plannedRpe = RpeTarget.exact(8.0),
    actualWeight = weight?.let(Weight::of),
    actualReps = reps,
    actualRpe = null,
    completed = completed,
    notes = null,
)

fun exerciseLog(
    id: Long,
    exercise: Exercise = squat,
    type: ExerciseType = ExerciseType.TOP,
    sets: List<SetLog>,
) = ExerciseLog(id = id, position = 1, exercise = exercise, type = type, sets = sets)

fun session(
    id: Long,
    templateId: Long? = id,
    date: LocalDate = LocalDate.of(2026, 1, 1),
    status: WorkoutStatus = WorkoutStatus.COMPLETED,
    weekNumber: Int = 1,
    dayNumber: Int = 1,
    exercises: List<ExerciseLog> = emptyList(),
) = WorkoutSession(
    id = id,
    templateId = templateId,
    weekNumber = weekNumber,
    dayNumber = dayNumber,
    title = "Squat Focus / Bench Volume",
    isDeload = false,
    status = status,
    date = date,
    startedAt = Instant.parse("2026-01-01T10:00:00Z"),
    finishedAt = if (status == WorkoutStatus.IN_PROGRESS) null else Instant.parse("2026-01-01T11:00:00Z"),
    notes = null,
    exercises = exercises,
)

fun template(
    id: Long,
    weekNumber: Int,
    dayNumber: Int,
    sequenceNumber: Int,
) = WorkoutTemplate(
    id = id,
    weekId = weekNumber.toLong(),
    weekNumber = weekNumber,
    dayNumber = dayNumber,
    sequenceNumber = sequenceNumber,
    title = "Day $dayNumber",
    isDeload = weekNumber == 6,
    exercises = listOf(
        ExerciseTemplate(
            id = id * 10,
            position = 1,
            exercise = squat,
            type = ExerciseType.TOP,
            plannedSets = listOf(
                PlannedSet(id * 100, 1, Weight.of(187.5), 3, RpeTarget.exact(8.0)),
            ),
        ),
    ),
)

/**
 * An in-memory plan covering the six-week, three-day layout of the real block.
 *
 * Creating one is deliberately absent, exactly as it is from the real repository: a program
 * only ever comes into existence as part of a cycle, so [FakeTrainingCycleRepository] is what
 * installs one here.
 */
class FakeTrainingProgramRepository(
    initialTemplates: List<WorkoutTemplate> = defaultTemplates(),
    programExists: Boolean = true,
) : TrainingProgramRepository {

    private val state = MutableStateFlow(
        PlanState(
            templates = initialTemplates,
            currentId = initialTemplates.firstOrNull()?.id,
            exists = programExists,
        ),
    )

    var setCurrentCalls: Int = 0
        private set

    override suspend fun hasProgram(): Boolean = state.value.exists

    override fun observeActiveProgram(): Flow<TrainingProgram?> = state.map { plan ->
        if (!plan.exists) {
            null
        } else {
            TrainingProgram(
                id = 1,
                name = "Blok IV",
                weeks = plan.templates.groupBy { it.weekNumber }.map { (week, workouts) ->
                    TrainingWeek(week.toLong(), 1, week, "W$week", week == 6, workouts)
                },
            )
        }
    }

    override suspend fun getActiveProgram(): TrainingProgram? = null

    override fun observeCurrentWorkoutTemplate(): Flow<WorkoutTemplate?> =
        state.map { plan -> plan.templates.firstOrNull { it.id == plan.currentId } }

    override suspend fun getCurrentWorkoutTemplate(): WorkoutTemplate? =
        state.value.let { plan -> plan.templates.firstOrNull { it.id == plan.currentId } }

    override fun observeCurrentTrainingWeek(): Flow<TrainingWeek?> = MutableStateFlow(null)

    override suspend fun getWorkoutTemplate(id: Long): WorkoutTemplate? =
        state.value.templates.firstOrNull { it.id == id }

    override suspend fun getWorkoutTemplateAfter(sequenceNumber: Int): WorkoutTemplate? =
        state.value.templates
            .filter { it.sequenceNumber > sequenceNumber }
            .minByOrNull { it.sequenceNumber }

    override suspend fun setCurrentWorkoutTemplate(templateId: Long?) {
        setCurrentCalls++
        state.value = state.value.copy(currentId = templateId)
    }

    /** What starting a cycle does to the plan: a whole block, pointer at its first unit. */
    internal fun install(templates: List<WorkoutTemplate>) {
        state.value = PlanState(templates = templates, currentId = templates.firstOrNull()?.id, exists = true)
    }

    internal fun snapshot(): PlanState = state.value

    internal fun restore(snapshot: PlanState) {
        state.value = snapshot
    }

    internal data class PlanState(
        val templates: List<WorkoutTemplate>,
        val currentId: Long?,
        val exists: Boolean,
    )

    companion object {
        fun defaultTemplates(): List<WorkoutTemplate> {
            var sequence = 0
            return (1..6).flatMap { week ->
                (1..3).map { day ->
                    sequence++
                    template(
                        id = sequence.toLong(),
                        weekNumber = week,
                        dayNumber = day,
                        sequenceNumber = sequence,
                    )
                }
            }
        }

        /** A plan-less installation: what a device looks like before first-run setup. */
        fun empty(): FakeTrainingProgramRepository =
            FakeTrainingProgramRepository(initialTemplates = emptyList(), programExists = false)
    }
}

/**
 * Cycles in memory, wired to the same plan and reference max fakes the rest of a test reads.
 *
 * [startCycle] mirrors the one contract the Room implementation has to honour: the cycle row,
 * the plan, the pointer and the maxes land together or not at all. Everything a test asserts
 * about atomicity is asserted against that.
 */
class FakeTrainingCycleRepository(
    private val programs: FakeTrainingProgramRepository,
    private val referenceMaxes: FakeReferenceMaxRepository,
    private val templatesOf: () -> List<WorkoutTemplate> =
        FakeTrainingProgramRepository::defaultTemplates,
) : TrainingCycleRepository {

    private val cycles = MutableStateFlow<List<TrainingCycle>>(emptyList())

    /** Every plan handed to [startCycle], in order — one per cycle that actually started. */
    val startedPrograms = mutableListOf<GeneratedProgram>()

    /** Set to make persistence blow up on the plan, the way a full disk would. */
    var failOnStart: Boolean = false

    /** Completed workouts inside the cycle currently being trained. */
    var completedWorkouts: Int = 0

    override fun observeCurrentCycle(): Flow<TrainingCycle?> =
        cycles.map { list -> list.maxByOrNull { it.cycleNumber } }

    override suspend fun getCurrentCycle(): TrainingCycle? =
        cycles.value.maxByOrNull { it.cycleNumber }

    override suspend fun getCycle(id: Long): TrainingCycle? = cycles.value.firstOrNull { it.id == id }

    override fun observeCycleSummaries(): Flow<List<TrainingCycleSummary>> = cycles.map { list ->
        list.sortedByDescending { it.cycleNumber }.map(::summaryOf)
    }

    override suspend fun getCycleSummary(cycleId: Long): TrainingCycleSummary? =
        cycles.value.firstOrNull { it.id == cycleId }?.let(::summaryOf)

    override suspend fun getCycleProgram(cycleId: Long): TrainingProgram? = null

    override suspend fun startCycle(
        program: GeneratedProgram,
        referenceMaxes: ReferenceMaxSnapshot,
        date: LocalDate,
        startedAt: Instant,
    ): TrainingCycle {
        val cyclesBefore = cycles.value
        val programsBefore = startedPrograms.toList()
        val planBefore = programs.snapshot()
        val maxesBefore = this.referenceMaxes.snapshot()

        return try {
            if (failOnStart) throw IllegalStateException("could not write the program")

            val previous = getCurrentCycle()
            if (previous != null && previous.isActive) {
                cycles.value = cycles.value.map {
                    if (it.id == previous.id) {
                        it.copy(status = CycleStatus.COMPLETED, completedAt = startedAt)
                    } else {
                        it
                    }
                }
            }

            val cycle = TrainingCycle(
                id = (cycles.value.maxOfOrNull { it.id } ?: 0L) + 1,
                cycleNumber = (previous?.cycleNumber ?: 0) + 1,
                status = CycleStatus.ACTIVE,
                startedAt = startedAt,
                completedAt = null,
                referenceMaxes = referenceMaxes,
                createdAt = startedAt,
            )
            cycles.value += cycle
            startedPrograms += program
            programs.install(templatesOf())
            completedWorkouts = 0
            this.referenceMaxes.writeAll(referenceMaxes.toReferenceMaxes(date))
            cycle
        } catch (failure: Throwable) {
            cycles.value = cyclesBefore
            startedPrograms.clear()
            startedPrograms += programsBefore
            programs.restore(planBefore)
            this.referenceMaxes.restore(maxesBefore)
            throw failure
        }
    }

    override suspend fun completeCurrentCycle(completedAt: Instant): TrainingCycle? {
        programs.setCurrentWorkoutTemplate(null)
        val current = getCurrentCycle() ?: return null
        if (current.isCompleted) return current

        val closed = current.copy(status = CycleStatus.COMPLETED, completedAt = completedAt)
        cycles.value = cycles.value.map { if (it.id == closed.id) closed else it }
        return closed
    }

    private fun summaryOf(cycle: TrainingCycle): TrainingCycleSummary {
        // A finished cycle counts as fully trained; the one in progress counts what the test
        // says has been logged, three days at a time.
        var remaining = if (cycle.isCompleted) WEEKS * DAYS_PER_WEEK else completedWorkouts
        return TrainingCycleSummary(
            cycle = cycle,
            weeks = (1..WEEKS).map { week ->
                val done = remaining.coerceIn(0, DAYS_PER_WEEK)
                remaining -= done
                CycleWeekProgress(
                    weekNumber = week,
                    label = if (week == WEEKS) "DELOAD" else "W$week",
                    isDeload = week == WEEKS,
                    plannedWorkouts = DAYS_PER_WEEK,
                    completedWorkouts = done,
                )
            },
        )
    }

    private companion object {
        const val WEEKS = 6
        const val DAYS_PER_WEEK = 3
    }
}

/** The same conversion the infrastructure mapper does, so tests read the maxes back as rows. */
private fun ReferenceMaxSnapshot.toReferenceMaxes(updatedOn: LocalDate): List<ReferenceMax> =
    byCategory.map { (category, weight) -> ReferenceMax(category, weight, updatedOn) }

class FakeWorkoutSessionRepository(
    initialSessions: List<WorkoutSession> = emptyList(),
) : WorkoutSessionRepository {

    private val sessions = MutableStateFlow(initialSessions)

    var completedWith: Pair<Long, TrainingVolume>? = null
        private set
    var startedFrom: WorkoutTemplate? = null
        private set
    val updatedSets = mutableListOf<Pair<Long, SetResult>>()

    override fun observeActiveSession(): Flow<WorkoutSession?> =
        sessions.map { list -> list.firstOrNull { it.isActive } }

    override suspend fun getActiveSession(): WorkoutSession? =
        sessions.value.firstOrNull { it.isActive }

    override suspend fun hasAnySession(): Boolean = sessions.value.isNotEmpty()

    override fun observeSession(id: Long): Flow<WorkoutSession?> =
        sessions.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getSession(id: Long): WorkoutSession? =
        sessions.value.firstOrNull { it.id == id }

    override fun observeHistory(): Flow<List<WorkoutSession>> =
        sessions.map { list -> list.filter { it.status.isFinished } }

    override fun observeCompletedSessions(): Flow<List<WorkoutSession>> =
        sessions.map { list ->
            list.filter { it.status == WorkoutStatus.COMPLETED }.sortedBy { it.date }
        }

    override suspend fun startSession(
        template: WorkoutTemplate,
        date: LocalDate,
        startedAt: Instant,
    ): Long {
        startedFrom = template
        val id = (sessions.value.maxOfOrNull { it.id } ?: 0L) + 1
        sessions.value += session(
            id = id,
            templateId = template.id,
            date = date,
            status = WorkoutStatus.IN_PROGRESS,
            weekNumber = template.weekNumber,
            dayNumber = template.dayNumber,
        )
        return id
    }

    override suspend fun updateSet(setLogId: Long, result: SetResult) {
        updatedSets += setLogId to result
    }

    override suspend fun completeSession(
        sessionId: Long,
        finishedAt: Instant,
        totalVolume: TrainingVolume,
    ) {
        completedWith = sessionId to totalVolume
        sessions.value = sessions.value.map {
            if (it.id == sessionId) it.copy(status = WorkoutStatus.COMPLETED, finishedAt = finishedAt) else it
        }
    }

    override suspend fun cancelSession(sessionId: Long, finishedAt: Instant) {
        sessions.value = sessions.value.map {
            if (it.id == sessionId) it.copy(status = WorkoutStatus.CANCELLED, finishedAt = finishedAt) else it
        }
    }

    override suspend fun updateSessionNotes(sessionId: Long, notes: String?) = Unit

    override suspend fun addExercise(sessionId: Long, exerciseId: Long, type: ExerciseType): Long = 99

    override suspend fun addSet(exerciseLogId: Long): Long = 99

    override suspend fun removeSet(setLogId: Long) = Unit
}

class FakeExerciseRepository(
    private val exercises: List<Exercise> = listOf(squat, deadlift, bench, accessory),
) : ExerciseRepository {
    override fun observeExercises(): Flow<List<Exercise>> = MutableStateFlow(exercises)
    override suspend fun getExercise(id: Long): Exercise? = exercises.firstOrNull { it.id == id }
}

class FakeReferenceMaxRepository(
    initial: List<ReferenceMax> = defaultReferenceMaxes(),
) : ReferenceMaxRepository {

    private val state = MutableStateFlow(initial)

    /** Set to make the maxes fail to persist, the way a full disk would. */
    var failOnWrite: Boolean = false

    /** Maxes written through the repository API rather than alongside a plan. */
    var standaloneWrites: Int = 0
        private set

    override fun observeReferenceMaxes(): Flow<List<ReferenceMax>> = state

    override suspend fun getReferenceMax(category: ExerciseCategory): ReferenceMax? =
        state.value.firstOrNull { it.category == category }

    override suspend fun updateReferenceMax(
        category: ExerciseCategory,
        weight: Weight,
        updatedOn: LocalDate,
    ) {
        standaloneWrites++
        write(ReferenceMax(category, weight, updatedOn))
    }

    override suspend fun hasAnyReferenceMax(): Boolean = state.value.isNotEmpty()

    /** Writes several maxes at once, as one upsert of the same rows would. */
    internal fun writeAll(referenceMaxes: List<ReferenceMax>) = referenceMaxes.forEach(::write)

    internal fun snapshot(): List<ReferenceMax> = state.value

    internal fun restore(snapshot: List<ReferenceMax>) {
        state.value = snapshot
    }

    private fun write(referenceMax: ReferenceMax) {
        if (failOnWrite) throw IllegalStateException("could not write the reference max")
        state.value = if (state.value.any { it.category == referenceMax.category }) {
            state.value.map { if (it.category == referenceMax.category) referenceMax else it }
        } else {
            state.value + referenceMax
        }
    }

    companion object {
        fun defaultReferenceMaxes(): List<ReferenceMax> = listOf(
            ReferenceMax(ExerciseCategory.SQUAT, Weight.of(210.0), LocalDate.of(2026, 1, 1)),
            ReferenceMax(ExerciseCategory.DEADLIFT, Weight.of(225.0), LocalDate.of(2026, 1, 1)),
            ReferenceMax(ExerciseCategory.BENCH_PRESS, Weight.of(170.0), LocalDate.of(2026, 1, 1)),
        )
    }
}

/** In-memory stand-in for the DataStore-backed first run flag. */
class FakeOnboardingRepository(private var completed: Boolean = false) : OnboardingRepository {

    var markCalls: Int = 0
        private set

    override suspend fun isOnboardingCompleted(): Boolean = completed

    override suspend fun markOnboardingCompleted() {
        markCalls++
        completed = true
    }
}
