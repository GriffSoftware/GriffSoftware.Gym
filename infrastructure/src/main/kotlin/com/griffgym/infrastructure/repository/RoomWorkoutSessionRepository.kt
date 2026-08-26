package com.griffgym.infrastructure.repository

import androidx.room.withTransaction
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.SetResult
import com.griffgym.domain.model.TrainingVolume
import com.griffgym.domain.model.WorkoutSession
import com.griffgym.domain.model.WorkoutStatus
import com.griffgym.domain.model.WorkoutTemplate
import com.griffgym.domain.repository.WorkoutSessionRepository
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.dao.WorkoutSessionDao
import com.griffgym.infrastructure.database.entity.ExerciseLogEntity
import com.griffgym.infrastructure.database.entity.SetLogEntity
import com.griffgym.infrastructure.database.entity.WorkoutSessionEntity
import com.griffgym.infrastructure.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomWorkoutSessionRepository @Inject constructor(
    private val database: GriffGymDatabase,
    private val sessionDao: WorkoutSessionDao,
) : WorkoutSessionRepository {

    override fun observeActiveSession(): Flow<WorkoutSession?> =
        sessionDao.observeByStatus(WorkoutStatus.IN_PROGRESS).map { it?.toDomain() }

    override suspend fun getActiveSession(): WorkoutSession? =
        sessionDao.getByStatus(WorkoutStatus.IN_PROGRESS)?.toDomain()

    override fun observeSession(id: Long): Flow<WorkoutSession?> =
        sessionDao.observeById(id).map { it?.toDomain() }

    override suspend fun getSession(id: Long): WorkoutSession? = sessionDao.getById(id)?.toDomain()

    override fun observeHistory(): Flow<List<WorkoutSession>> =
        sessionDao.observeHistory().map { sessions -> sessions.map { it.toDomain() } }

    override fun observeCompletedSessions(): Flow<List<WorkoutSession>> =
        sessionDao.observeCompleted().map { sessions -> sessions.map { it.toDomain() } }

    /**
     * Copies the template into session rows in a single transaction.
     *
     * Every prescribed set becomes its own `set_log` row carrying the planned load, reps
     * and RPE — the snapshot that makes history immutable even if the plan is edited later.
     */
    override suspend fun startSession(
        template: WorkoutTemplate,
        date: LocalDate,
        startedAt: Instant,
    ): Long = database.withTransaction {
        val sessionId = sessionDao.insertSession(
            WorkoutSessionEntity(
                templateId = template.id,
                weekNumber = template.weekNumber,
                dayNumber = template.dayNumber,
                title = template.title,
                isDeload = template.isDeload,
                status = WorkoutStatus.IN_PROGRESS,
                date = date.toEpochDay(),
                startedAt = startedAt,
                finishedAt = null,
                totalVolumeKg = null,
                notes = null,
            ),
        )

        template.exercises.sortedBy { it.position }.forEachIndexed { exerciseIndex, exercise ->
            val logId = sessionDao.insertExerciseLog(
                ExerciseLogEntity(
                    sessionId = sessionId,
                    exerciseId = exercise.exercise.id,
                    type = exercise.type,
                    position = exerciseIndex + 1,
                ),
            )
            val sets = exercise.plannedSets.sortedBy { it.position }.mapIndexed { setIndex, planned ->
                SetLogEntity(
                    exerciseLogId = logId,
                    position = setIndex + 1,
                    plannedWeightKg = planned.weight?.kilograms,
                    plannedReps = planned.reps,
                    plannedRpeMin = planned.targetRpe?.min?.value,
                    plannedRpeMax = planned.targetRpe?.max?.value,
                    // Pre-filling the plan as the starting point is what makes logging fast:
                    // the lifter only touches the fields where reality differed.
                    actualWeightKg = planned.weight?.kilograms,
                    actualReps = planned.reps,
                    actualRpe = null,
                    completed = false,
                    notes = null,
                )
            }
            if (sets.isNotEmpty()) sessionDao.insertSetLogs(sets)
        }

        sessionId
    }

    override suspend fun updateSet(setLogId: Long, result: SetResult) {
        sessionDao.updateSetResult(
            id = setLogId,
            weightKg = result.weight?.kilograms,
            reps = result.reps,
            rpe = result.rpe?.value,
            completed = result.completed,
            notes = result.notes,
        )
    }

    override suspend fun completeSession(
        sessionId: Long,
        finishedAt: Instant,
        totalVolume: TrainingVolume,
    ) {
        sessionDao.finishSession(
            id = sessionId,
            status = WorkoutStatus.COMPLETED,
            finishedAt = finishedAt,
            totalVolumeKg = totalVolume.kilograms,
        )
    }

    override suspend fun cancelSession(sessionId: Long, finishedAt: Instant) {
        sessionDao.finishSession(
            id = sessionId,
            status = WorkoutStatus.CANCELLED,
            finishedAt = finishedAt,
            totalVolumeKg = null,
        )
    }

    override suspend fun updateSessionNotes(sessionId: Long, notes: String?) {
        sessionDao.updateNotes(sessionId, notes)
    }

    override suspend fun addExercise(
        sessionId: Long,
        exerciseId: Long,
        type: ExerciseType,
    ): Long = database.withTransaction {
        val position = sessionDao.lastExercisePosition(sessionId) + 1
        val logId = sessionDao.insertExerciseLog(
            ExerciseLogEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                type = type,
                position = position,
            ),
        )
        // An unplanned exercise starts with one empty set so there is something to type into.
        sessionDao.insertSetLog(emptySet(logId, position = 1))
        logId
    }

    override suspend fun addSet(exerciseLogId: Long): Long = database.withTransaction {
        val position = sessionDao.lastSetPosition(exerciseLogId) + 1
        val previous = sessionDao.lastSetOf(exerciseLogId)
        sessionDao.insertSetLog(
            emptySet(exerciseLogId, position).copy(
                // Carry the load and reps forward so an extra set is one tap, not four.
                actualWeightKg = previous?.actualWeightKg,
                actualReps = previous?.actualReps,
            ),
        )
    }

    override suspend fun removeSet(setLogId: Long) {
        sessionDao.deleteSetLog(setLogId)
    }

    private fun emptySet(exerciseLogId: Long, position: Int) = SetLogEntity(
        exerciseLogId = exerciseLogId,
        position = position,
        plannedWeightKg = null,
        plannedReps = null,
        plannedRpeMin = null,
        plannedRpeMax = null,
        actualWeightKg = null,
        actualReps = null,
        actualRpe = null,
        completed = false,
        notes = null,
    )
}
