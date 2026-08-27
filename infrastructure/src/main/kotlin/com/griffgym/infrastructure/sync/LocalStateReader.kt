package com.griffgym.infrastructure.sync

import com.griffgym.infrastructure.database.dao.CloudSyncDao
import com.griffgym.infrastructure.sync.model.CloudSnapshot
import com.griffgym.infrastructure.sync.model.SnapshotCycle
import com.griffgym.infrastructure.sync.model.SnapshotExercise
import com.griffgym.infrastructure.sync.model.SnapshotExerciseLog
import com.griffgym.infrastructure.sync.model.SnapshotExerciseTemplate
import com.griffgym.infrastructure.sync.model.SnapshotPlannedSet
import com.griffgym.infrastructure.sync.model.SnapshotProgram
import com.griffgym.infrastructure.sync.model.SnapshotReferenceMax
import com.griffgym.infrastructure.sync.model.SnapshotSetLog
import com.griffgym.infrastructure.sync.model.SnapshotWeek
import com.griffgym.infrastructure.sync.model.SnapshotWorkout
import com.griffgym.infrastructure.sync.model.SnapshotWorkoutTemplate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the whole local database into a [CloudSnapshot].
 *
 * Twelve bulk selects and then assembly in memory, rather than a nest of Room relations. A
 * lifter several years in has tens of thousands of set logs, and `@Relation` would issue one
 * query per parent to fetch them — the classic N+1, except the N here is "every workout you
 * have ever done". Twelve queries and a few hash maps is both faster and easier to reason
 * about when something does not line up.
 */
@Singleton
internal class LocalStateReader @Inject constructor(
    private val cloudSyncDao: CloudSyncDao,
) {

    suspend fun read(): CloudSnapshot {
        val exercises = cloudSyncDao.allExercises()
        val cycles = cloudSyncDao.allCycles()
        val programs = cloudSyncDao.allPrograms()
        val weeks = cloudSyncDao.allWeeks()
        val workoutTemplates = cloudSyncDao.allWorkoutTemplates()
        val exerciseTemplates = cloudSyncDao.allExerciseTemplates()
        val plannedSets = cloudSyncDao.allPlannedSets()
        val progress = cloudSyncDao.allProgramProgress()
        val sessions = cloudSyncDao.allWorkoutSessions()
        val exerciseLogs = cloudSyncDao.allExerciseLogs()
        val setLogs = cloudSyncDao.allSetLogs()
        val referenceMaxes = cloudSyncDao.allReferenceMaxes()

        val exerciseById = exercises.associateBy { it.id }
        val plannedSetsByTemplate = plannedSets.groupBy { it.exerciseTemplateId }
        val exerciseTemplatesByWorkout = exerciseTemplates.groupBy { it.workoutTemplateId }
        val workoutTemplatesByWeek = workoutTemplates.groupBy { it.weekId }
        val weeksByProgram = weeks.groupBy { it.programId }
        val programByCycle = programs.associateBy { it.cycleId }
        val progressByProgram = progress.associateBy { it.programId }
        val setLogsByExerciseLog = setLogs.groupBy { it.exerciseLogId }
        val exerciseLogsBySession = exerciseLogs.groupBy { it.sessionId }
        val workoutTemplateById = workoutTemplates.associateBy { it.id }

        // A session points at a template, which belongs to a week, which belongs to a program,
        // which belongs to a cycle. The API wants the cycle directly, so the chain is walked
        // once here rather than at every call site.
        val weekById = weeks.associateBy { it.id }
        val programById = programs.associateBy { it.id }
        val cycleSyncIdByTemplateId: Map<Long, String> = workoutTemplates.mapNotNull { template ->
            val week = weekById[template.weekId] ?: return@mapNotNull null
            val program = programById[week.programId] ?: return@mapNotNull null
            val cycle = cycles.firstOrNull { it.id == program.cycleId } ?: return@mapNotNull null
            template.id to cycle.syncId
        }.toMap()

        return CloudSnapshot(
            exercises = exercises.map {
                SnapshotExercise(it.syncId, it.name, it.category)
            },
            referenceMaxes = referenceMaxes.map {
                SnapshotReferenceMax(it.syncId, it.category, it.weightKg, java.time.LocalDate.ofEpochDay(it.updatedOn))
            },
            cycles = cycles.map { cycle ->
                val program = programByCycle[cycle.id]

                SnapshotCycle(
                    syncId = cycle.syncId,
                    cycleNumber = cycle.cycleNumber,
                    status = cycle.status,
                    startedAt = cycle.startedAt,
                    completedAt = cycle.completedAt,
                    squatKg = cycle.squatKg,
                    benchPressKg = cycle.benchPressKg,
                    deadliftKg = cycle.deadliftKg,
                    createdAt = cycle.createdAt,
                    program = SnapshotProgram(
                        syncId = program?.syncId.orEmpty(),
                        name = program?.name.orEmpty(),
                        createdAt = program?.createdAt ?: cycle.createdAt,
                        isActive = program?.isActive == true,
                        currentWorkoutTemplateSyncId = program
                            ?.let { progressByProgram[it.id]?.currentWorkoutTemplateId }
                            ?.let { workoutTemplateById[it]?.syncId },
                        weeks = weeksByProgram[program?.id].orEmpty()
                            .sortedBy { it.weekNumber }
                            .map { week ->
                                SnapshotWeek(
                                    syncId = week.syncId,
                                    weekNumber = week.weekNumber,
                                    label = week.label,
                                    isDeload = week.isDeload,
                                    workouts = workoutTemplatesByWeek[week.id].orEmpty()
                                        .sortedBy { it.dayNumber }
                                        .map { template ->
                                            SnapshotWorkoutTemplate(
                                                syncId = template.syncId,
                                                dayNumber = template.dayNumber,
                                                sequenceNumber = template.sequenceNumber,
                                                title = template.title,
                                                exercises = exerciseTemplatesByWorkout[template.id]
                                                    .orEmpty()
                                                    .sortedBy { it.position }
                                                    .map { exerciseTemplate ->
                                                        val exercise =
                                                            exerciseById[exerciseTemplate.exerciseId]

                                                        SnapshotExerciseTemplate(
                                                            syncId = exerciseTemplate.syncId,
                                                            exerciseSyncId = exercise?.syncId.orEmpty(),
                                                            exerciseName = exercise?.name.orEmpty(),
                                                            exerciseCategory = exercise?.category
                                                                ?: com.griffgym.domain.model.ExerciseCategory.ACCESSORY,
                                                            type = exerciseTemplate.type,
                                                            position = exerciseTemplate.position,
                                                            plannedSets =
                                                                plannedSetsByTemplate[exerciseTemplate.id]
                                                                    .orEmpty()
                                                                    .sortedBy { it.position }
                                                                    .map { set ->
                                                                        SnapshotPlannedSet(
                                                                            syncId = set.syncId,
                                                                            position = set.position,
                                                                            weightKg = set.weightKg,
                                                                            reps = set.reps,
                                                                            rpeMin = set.rpeMin,
                                                                            rpeMax = set.rpeMax,
                                                                        )
                                                                    },
                                                        )
                                                    },
                                            )
                                        },
                                )
                            },
                    ),
                )
            },
            workouts = sessions.map { session ->
                SnapshotWorkout(
                    syncId = session.syncId,
                    templateSyncId = session.templateId?.let { workoutTemplateById[it]?.syncId },
                    cycleSyncId = session.templateId?.let { cycleSyncIdByTemplateId[it] },
                    weekNumber = session.weekNumber,
                    dayNumber = session.dayNumber,
                    title = session.title,
                    isDeload = session.isDeload,
                    status = session.status,
                    date = java.time.LocalDate.ofEpochDay(session.date),
                    startedAt = session.startedAt,
                    finishedAt = session.finishedAt,
                    totalVolumeKg = session.totalVolumeKg,
                    notes = session.notes,
                    exercises = exerciseLogsBySession[session.id].orEmpty()
                        .sortedBy { it.position }
                        .map { log ->
                            val exercise = exerciseById[log.exerciseId]

                            SnapshotExerciseLog(
                                syncId = log.syncId,
                                exerciseSyncId = exercise?.syncId,
                                exerciseName = exercise?.name.orEmpty(),
                                exerciseCategory = exercise?.category
                                    ?: com.griffgym.domain.model.ExerciseCategory.ACCESSORY,
                                type = log.type,
                                position = log.position,
                                sets = setLogsByExerciseLog[log.id].orEmpty()
                                    .sortedBy { it.position }
                                    .map { set ->
                                        SnapshotSetLog(
                                            syncId = set.syncId,
                                            position = set.position,
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
                        },
                )
            },
        )
    }
}
