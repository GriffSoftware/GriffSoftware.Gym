package com.griffgym.application

import com.griffgym.domain.model.Exercise
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseLog
import com.griffgym.domain.model.ExerciseTemplate
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.PlannedSet
import com.griffgym.domain.model.ReferenceMax
import com.griffgym.domain.model.RpeTarget
import com.griffgym.domain.model.SetLog
import com.griffgym.domain.model.SetResult
import com.griffgym.domain.model.TrainingProgram
import com.griffgym.domain.model.TrainingVolume
import com.griffgym.domain.model.TrainingWeek
import com.griffgym.domain.model.Weight
import com.griffgym.domain.model.WorkoutSession
import com.griffgym.domain.model.WorkoutStatus
import com.griffgym.domain.model.WorkoutTemplate
import com.griffgym.domain.repository.ExerciseRepository
import com.griffgym.domain.repository.ReferenceMaxRepository
import com.griffgym.domain.repository.TrainingProgramRepository
import com.griffgym.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

val squat = Exercise(1, "Przysiad", ExerciseCategory.SQUAT)
val deadlift = Exercise(2, "Martwy ciąg", ExerciseCategory.DEADLIFT)
val bench = Exercise(3, "Ławka", ExerciseCategory.BENCH_PRESS)
val accessory = Exercise(4, "Skos Smith", ExerciseCategory.ACCESSORY)

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

/** An in-memory program covering the six-week, three-day layout of the real block. */
class FakeTrainingProgramRepository(
    private val templates: List<WorkoutTemplate> = defaultTemplates(),
) : TrainingProgramRepository {

    private val currentId = MutableStateFlow<Long?>(templates.firstOrNull()?.id)

    var setCurrentCalls: Int = 0
        private set

    override fun observeActiveProgram(): Flow<TrainingProgram?> = MutableStateFlow(
        TrainingProgram(
            id = 1,
            name = "Blok IV",
            weeks = templates.groupBy { it.weekNumber }.map { (week, workouts) ->
                TrainingWeek(week.toLong(), 1, week, "W$week", week == 6, workouts)
            },
        ),
    ).asStateFlow()

    override suspend fun getActiveProgram(): TrainingProgram? = null

    override fun observeCurrentWorkoutTemplate(): Flow<WorkoutTemplate?> =
        currentId.map { id -> templates.firstOrNull { it.id == id } }

    override suspend fun getCurrentWorkoutTemplate(): WorkoutTemplate? =
        templates.firstOrNull { it.id == currentId.value }

    override fun observeCurrentTrainingWeek(): Flow<TrainingWeek?> = MutableStateFlow(null)

    override suspend fun getWorkoutTemplate(id: Long): WorkoutTemplate? =
        templates.firstOrNull { it.id == id }

    override suspend fun getWorkoutTemplateAfter(sequenceNumber: Int): WorkoutTemplate? =
        templates.filter { it.sequenceNumber > sequenceNumber }.minByOrNull { it.sequenceNumber }

    override suspend fun setCurrentWorkoutTemplate(templateId: Long?) {
        setCurrentCalls++
        currentId.value = templateId
    }

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
    }
}

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

class FakeReferenceMaxRepository : ReferenceMaxRepository {
    private val state = MutableStateFlow(
        listOf(
            ReferenceMax(ExerciseCategory.SQUAT, Weight.of(210.0), LocalDate.of(2026, 1, 1)),
            ReferenceMax(ExerciseCategory.DEADLIFT, Weight.of(225.0), LocalDate.of(2026, 1, 1)),
            ReferenceMax(ExerciseCategory.BENCH_PRESS, Weight.of(170.0), LocalDate.of(2026, 1, 1)),
        ),
    )

    override fun observeReferenceMaxes(): Flow<List<ReferenceMax>> = state

    override suspend fun getReferenceMax(category: ExerciseCategory): ReferenceMax? =
        state.value.firstOrNull { it.category == category }

    override suspend fun updateReferenceMax(
        category: ExerciseCategory,
        weight: Weight,
        updatedOn: LocalDate,
    ) {
        state.value = state.value.map {
            if (it.category == category) ReferenceMax(category, weight, updatedOn) else it
        }
    }
}
