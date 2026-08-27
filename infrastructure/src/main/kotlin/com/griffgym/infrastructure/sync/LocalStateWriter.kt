package com.griffgym.infrastructure.sync

import androidx.room.withTransaction
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.dao.CloudSyncDao
import com.griffgym.infrastructure.database.dao.SyncMetadataDao
import com.griffgym.infrastructure.database.entity.ExerciseEntity
import com.griffgym.infrastructure.database.entity.ExerciseLogEntity
import com.griffgym.infrastructure.database.entity.ExerciseTemplateEntity
import com.griffgym.infrastructure.database.entity.PlannedSetEntity
import com.griffgym.infrastructure.database.entity.ProgramProgressEntity
import com.griffgym.infrastructure.database.entity.ReferenceMaxEntity
import com.griffgym.infrastructure.database.entity.SetLogEntity
import com.griffgym.infrastructure.database.entity.SyncEntityType
import com.griffgym.infrastructure.database.entity.SyncMetadataEntity
import com.griffgym.infrastructure.database.entity.SyncState
import com.griffgym.infrastructure.database.entity.TrainingCycleEntity
import com.griffgym.infrastructure.database.entity.TrainingProgramEntity
import com.griffgym.infrastructure.database.entity.TrainingWeekEntity
import com.griffgym.infrastructure.database.entity.WorkoutSessionEntity
import com.griffgym.infrastructure.database.entity.WorkoutTemplateEntity
import com.griffgym.infrastructure.sync.model.CloudSnapshot
import com.griffgym.infrastructure.sync.model.SnapshotExerciseLog
import com.griffgym.infrastructure.sync.model.SnapshotExerciseTemplate
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rebuilds the local database from a [CloudSnapshot], as one transaction.
 *
 * The atomicity is the entire point. Restoring is a delete followed by eleven tables' worth of
 * inserts, and a failure in the middle would leave a lifter with cycles that have no plans and
 * sessions that have no sets — a database the app cannot tell apart from real data, so it would
 * render the wreckage as if it were their training history. One transaction means either the
 * whole history lands or the database is left exactly as it was.
 *
 * Row ids are assigned by SQLite as the tree goes in, and each level's sync id is mapped to the
 * id it got so the next level can be wired to it. That is why this cannot be a bulk insert per
 * table: the children need their parents' ids, and the ids do not exist until the parents are
 * written.
 */
@Singleton
internal class LocalStateWriter @Inject constructor(
    private val database: GriffGymDatabase,
    private val cloudSyncDao: CloudSyncDao,
    private val syncMetadataDao: SyncMetadataDao,
) {

    suspend fun replaceLocalState(snapshot: CloudSnapshot, restoredAt: Instant) {
        database.withTransaction {
            clearEverything()

            val exerciseIds = insertExercises(snapshot)
            insertReferenceMaxes(snapshot)

            val templateIds = insertCycles(snapshot, exerciseIds)
            insertWorkouts(snapshot, exerciseIds, templateIds)

            markEverythingSynced(snapshot, restoredAt)
        }
    }

    /** Wipes this account's cached training data without touching the cloud copy. */
    suspend fun clearLocalTrainingData() {
        database.withTransaction {
            clearEverything()
            syncMetadataDao.clear()
        }
    }

    private suspend fun clearEverything() {
        // Child-first. Foreign keys are enforced inside the transaction, so a parent deleted
        // while its children are still there fails the whole restore rather than orphaning rows.
        cloudSyncDao.deleteAllSetLogs()
        cloudSyncDao.deleteAllExerciseLogs()
        cloudSyncDao.deleteAllWorkoutSessions()
        cloudSyncDao.deleteAllProgramProgress()
        cloudSyncDao.deleteAllPlannedSets()
        cloudSyncDao.deleteAllExerciseTemplates()
        cloudSyncDao.deleteAllWorkoutTemplates()
        cloudSyncDao.deleteAllWeeks()
        cloudSyncDao.deleteAllPrograms()
        cloudSyncDao.deleteAllCycles()
        cloudSyncDao.deleteAllReferenceMaxes()
        cloudSyncDao.deleteAllExercises()
        syncMetadataDao.clear()
    }

    private suspend fun insertExercises(snapshot: CloudSnapshot): MutableMap<String, Long> {
        val ids = mutableMapOf<String, Long>()

        snapshot.exercises.forEach { exercise ->
            ids[exercise.syncId] = cloudSyncDao.insertExercise(
                ExerciseEntity(
                    syncId = exercise.syncId,
                    name = exercise.name,
                    category = exercise.category,
                ),
            )
        }

        return ids
    }

    private suspend fun insertReferenceMaxes(snapshot: CloudSnapshot) {
        cloudSyncDao.insertReferenceMaxes(
            snapshot.referenceMaxes.map { max ->
                ReferenceMaxEntity(
                    category = max.category,
                    syncId = max.syncId,
                    weightKg = max.weightKg,
                    updatedOn = max.updatedOn.toEpochDay(),
                )
            },
        )
    }

    /** Returns the workout template sync id -> row id map the sessions need. */
    private suspend fun insertCycles(
        snapshot: CloudSnapshot,
        exerciseIds: MutableMap<String, Long>,
    ): Map<String, Long> {
        val templateIds = mutableMapOf<String, Long>()

        snapshot.cycles.sortedBy { it.cycleNumber }.forEach { cycle ->
            val cycleId = cloudSyncDao.insertCycle(
                TrainingCycleEntity(
                    syncId = cycle.syncId,
                    cycleNumber = cycle.cycleNumber,
                    status = cycle.status,
                    startedAt = cycle.startedAt,
                    completedAt = cycle.completedAt,
                    // Restored exactly as stored. Recomputing a historical block from today's
                    // maxes would rewrite what the lifter actually trained.
                    squatKg = cycle.squatKg,
                    benchPressKg = cycle.benchPressKg,
                    deadliftKg = cycle.deadliftKg,
                    createdAt = cycle.createdAt,
                ),
            )

            val program = cycle.program
            val programId = cloudSyncDao.insertProgram(
                TrainingProgramEntity(
                    syncId = program.syncId,
                    cycleId = cycleId,
                    name = program.name,
                    createdAt = program.createdAt,
                    isActive = program.isActive,
                ),
            )

            program.weeks.sortedBy { it.weekNumber }.forEach { week ->
                val weekId = cloudSyncDao.insertWeek(
                    TrainingWeekEntity(
                        syncId = week.syncId,
                        programId = programId,
                        weekNumber = week.weekNumber,
                        label = week.label,
                        isDeload = week.isDeload,
                    ),
                )

                week.workouts.sortedBy { it.dayNumber }.forEach { template ->
                    val templateId = cloudSyncDao.insertWorkoutTemplate(
                        WorkoutTemplateEntity(
                            syncId = template.syncId,
                            weekId = weekId,
                            dayNumber = template.dayNumber,
                            sequenceNumber = template.sequenceNumber,
                            title = template.title,
                        ),
                    )
                    templateIds[template.syncId] = templateId

                    template.exercises.sortedBy { it.position }.forEach { exerciseTemplate ->
                        insertExerciseTemplate(exerciseTemplate, templateId, exerciseIds)
                    }
                }
            }

            // Written after every template exists, because the pointer names one of them.
            cloudSyncDao.insertProgramProgress(
                ProgramProgressEntity(
                    programId = programId,
                    currentWorkoutTemplateId = program.currentWorkoutTemplateSyncId
                        ?.let { templateIds[it] },
                ),
            )
        }

        return templateIds
    }

    private suspend fun insertExerciseTemplate(
        template: SnapshotExerciseTemplate,
        workoutTemplateId: Long,
        exerciseIds: MutableMap<String, Long>,
    ) {
        val exerciseTemplateId = cloudSyncDao.insertExerciseTemplate(
            ExerciseTemplateEntity(
                syncId = template.syncId,
                workoutTemplateId = workoutTemplateId,
                exerciseId = resolveExerciseId(
                    template.exerciseSyncId,
                    template.exerciseName,
                    template.exerciseCategory,
                    exerciseIds,
                ),
                type = template.type,
                position = template.position,
            ),
        )

        cloudSyncDao.insertPlannedSets(
            template.plannedSets.sortedBy { it.position }.map { set ->
                PlannedSetEntity(
                    syncId = set.syncId,
                    exerciseTemplateId = exerciseTemplateId,
                    position = set.position,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    rpeMin = set.rpeMin,
                    rpeMax = set.rpeMax,
                )
            },
        )
    }

    private suspend fun insertWorkouts(
        snapshot: CloudSnapshot,
        exerciseIds: MutableMap<String, Long>,
        templateIds: Map<String, Long>,
    ) {
        snapshot.workouts.sortedBy { it.startedAt }.forEach { workout ->
            val sessionId = cloudSyncDao.insertWorkoutSession(
                WorkoutSessionEntity(
                    syncId = workout.syncId,
                    // Provenance only, and nulled rather than dropped if the plan it came from
                    // is not in this snapshot. The snapshot columns below carry the truth.
                    templateId = workout.templateSyncId?.let { templateIds[it] },
                    weekNumber = workout.weekNumber,
                    dayNumber = workout.dayNumber,
                    title = workout.title,
                    isDeload = workout.isDeload,
                    status = workout.status,
                    date = workout.date.toEpochDay(),
                    startedAt = workout.startedAt,
                    finishedAt = workout.finishedAt,
                    totalVolumeKg = workout.totalVolumeKg,
                    notes = workout.notes,
                ),
            )

            workout.exercises.sortedBy { it.position }.forEach { log ->
                insertExerciseLog(log, sessionId, exerciseIds)
            }
        }
    }

    private suspend fun insertExerciseLog(
        log: SnapshotExerciseLog,
        sessionId: Long,
        exerciseIds: MutableMap<String, Long>,
    ) {
        val exerciseLogId = cloudSyncDao.insertExerciseLog(
            ExerciseLogEntity(
                syncId = log.syncId,
                sessionId = sessionId,
                exerciseId = resolveExerciseId(
                    log.exerciseSyncId,
                    log.exerciseName,
                    log.exerciseCategory,
                    exerciseIds,
                ),
                type = log.type,
                position = log.position,
            ),
        )

        cloudSyncDao.insertSetLogs(
            log.sets.sortedBy { it.position }.map { set ->
                SetLogEntity(
                    syncId = set.syncId,
                    exerciseLogId = exerciseLogId,
                    position = set.position,
                    // Planned and actual restored as two separate facts, exactly as they were
                    // recorded. Nothing here derives one from the other.
                    plannedWeightKg = set.plannedWeightKg,
                    plannedReps = set.plannedReps,
                    plannedRpeMin = set.plannedRpeMin,
                    plannedRpeMax = set.plannedRpeMax,
                    actualWeightKg = set.actualWeightKg,
                    actualReps = set.actualReps,
                    actualRpe = set.actualRpe,
                    completed = set.completed,
                    notes = set.notes,
                )
            },
        )
    }

    /**
     * The exercise a template or log points at, creating it from the snapshot's own name and
     * category if the catalogue does not contain it.
     *
     * A history that refers to a movement the catalogue has since lost must still restore.
     * Losing the row would take the exercise log with it, which is a real workout the lifter
     * did — so the movement is recreated from the snapshot rather than the record discarded.
     */
    private suspend fun resolveExerciseId(
        syncId: String?,
        name: String,
        category: com.griffgym.domain.model.ExerciseCategory,
        exerciseIds: MutableMap<String, Long>,
    ): Long {
        syncId?.let { exerciseIds[it] }?.let { return it }

        val fallbackSyncId = syncId
            ?: com.griffgym.infrastructure.database.entity.newSyncId()

        val id = cloudSyncDao.insertExercise(
            ExerciseEntity(
                syncId = fallbackSyncId,
                name = name.ifBlank { "Unknown exercise" },
                category = category,
            ),
        )
        exerciseIds[fallbackSyncId] = id

        return id
    }

    /**
     * The restored database came *from* the server, so re-uploading all of it would be pure
     * waste — and worse, would look like a phone full of unsynced changes the moment it opened.
     */
    private suspend fun markEverythingSynced(snapshot: CloudSnapshot, restoredAt: Instant) {
        val millis = restoredAt.toEpochMilli()

        fun entry(type: SyncEntityType, id: String) = SyncMetadataEntity(
            entityType = type,
            entityId = id,
            syncState = SyncState.SYNCED,
            serverVersion = null,
            lastAttemptAtUtc = millis,
            lastSyncedAtUtc = millis,
            failureMessage = null,
        )

        syncMetadataDao.upsertAll(
            buildList {
                snapshot.exercises.forEach { add(entry(SyncEntityType.EXERCISE, it.syncId)) }
                snapshot.referenceMaxes.forEach {
                    add(entry(SyncEntityType.REFERENCE_MAX, it.syncId))
                }
                snapshot.cycles.forEach { add(entry(SyncEntityType.TRAINING_CYCLE, it.syncId)) }
                snapshot.workouts.forEach { add(entry(SyncEntityType.WORKOUT_SESSION, it.syncId)) }
            },
        )
    }
}
