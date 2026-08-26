package com.griffgym.infrastructure.seed

import androidx.room.withTransaction
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.entity.ExerciseEntity
import com.griffgym.infrastructure.database.entity.ExerciseTemplateEntity
import com.griffgym.infrastructure.database.entity.PlannedSetEntity
import com.griffgym.infrastructure.database.entity.ProgramProgressEntity
import com.griffgym.infrastructure.database.entity.ReferenceMaxEntity
import com.griffgym.infrastructure.database.entity.TrainingProgramEntity
import com.griffgym.infrastructure.database.entity.TrainingWeekEntity
import com.griffgym.infrastructure.database.entity.WorkoutTemplateEntity
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills a fresh database with the exercise catalogue, the reference maxes and the
 * six-week program, then points the program at its first unit.
 *
 * Every step is guarded by an existence check and the whole thing runs inside one
 * transaction, so seeding is idempotent: reinstalling or updating the app can never
 * duplicate the plan or wipe logged history.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val database: GriffGymDatabase,
    private val clock: Clock,
) {

    suspend fun seedIfNeeded() {
        database.withTransaction {
            seedExercises()
            seedReferenceMaxes()
            seedProgram()
        }
    }

    private suspend fun seedExercises() {
        val dao = database.exerciseDao()
        val missing = TrainingPlanSeed.exercises
            .filter { dao.getByName(it.name) == null }
            .map { ExerciseEntity(name = it.name, category = it.category) }
        if (missing.isNotEmpty()) dao.insertAll(missing)
    }

    private suspend fun seedReferenceMaxes() {
        val dao = database.referenceMaxDao()
        if (dao.count() > 0) return
        val today = LocalDate.now(clock).toEpochDay()
        dao.upsertAll(
            TrainingPlanSeed.referenceMaxes.map { (category, weight) ->
                ReferenceMaxEntity(category = category, weightKg = weight, updatedOn = today)
            },
        )
    }

    private suspend fun seedProgram() {
        val programDao = database.trainingProgramDao()
        if (programDao.programCount() > 0) return

        val exerciseDao = database.exerciseDao()
        val exerciseIds = TrainingPlanSeed.exercises.associate { seed ->
            seed.name to requireNotNull(exerciseDao.getByName(seed.name)) {
                "Exercise '${seed.name}' was not seeded"
            }.id
        }

        val programId = programDao.insertProgram(
            TrainingProgramEntity(
                name = TrainingPlanSeed.PROGRAM_NAME,
                createdAt = clock.instant(),
                isActive = true,
            ),
        )

        var sequenceNumber = 0
        var firstTemplateId: Long? = null

        TrainingPlanSeed.weeks.forEach { week ->
            val weekId = programDao.insertWeek(
                TrainingWeekEntity(
                    programId = programId,
                    weekNumber = week.weekNumber,
                    label = week.label,
                    isDeload = week.isDeload,
                ),
            )

            week.days.forEach { day ->
                sequenceNumber += 1
                val templateId = programDao.insertWorkoutTemplate(
                    WorkoutTemplateEntity(
                        weekId = weekId,
                        dayNumber = day.dayNumber,
                        sequenceNumber = sequenceNumber,
                        title = day.title,
                    ),
                )
                if (firstTemplateId == null) firstTemplateId = templateId

                day.entries.forEachIndexed { index, entry ->
                    val exerciseTemplateId = programDao.insertExerciseTemplate(
                        ExerciseTemplateEntity(
                            workoutTemplateId = templateId,
                            exerciseId = exerciseIds.getValue(entry.exercise),
                            type = entry.type,
                            position = index + 1,
                        ),
                    )
                    // "3x3x175" is stored as three concrete rows rather than a scheme string,
                    // so a session can snapshot each set individually.
                    programDao.insertPlannedSets(
                        (1..entry.sets).map { position ->
                            PlannedSetEntity(
                                exerciseTemplateId = exerciseTemplateId,
                                position = position,
                                weightKg = entry.weightKg,
                                reps = entry.reps,
                                rpeMin = entry.rpe.min.value,
                                rpeMax = entry.rpe.max.value,
                            )
                        },
                    )
                }
            }
        }

        programDao.upsertProgress(
            ProgramProgressEntity(programId = programId, currentWorkoutTemplateId = firstTemplateId),
        )
    }
}
