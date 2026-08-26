package com.griffgym.infrastructure.repository

import com.griffgym.domain.model.GeneratedProgram
import com.griffgym.domain.model.TemplateExercise
import com.griffgym.infrastructure.database.dao.ExerciseDao
import com.griffgym.infrastructure.database.dao.TrainingProgramDao
import com.griffgym.infrastructure.database.entity.ExerciseEntity
import com.griffgym.infrastructure.database.entity.ExerciseTemplateEntity
import com.griffgym.infrastructure.database.entity.PlannedSetEntity
import com.griffgym.infrastructure.database.entity.ProgramProgressEntity
import com.griffgym.infrastructure.database.entity.TrainingProgramEntity
import com.griffgym.infrastructure.database.entity.TrainingWeekEntity
import com.griffgym.infrastructure.database.entity.WorkoutTemplateEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a generated block into the tables that hold it.
 *
 * Deliberately not a repository and deliberately not transactional on its own: it is always
 * called from inside the caller's transaction, because a plan is only ever written as part
 * of starting a cycle and must stand or fall with the cycle row and the reference maxes.
 * Keeping the row-by-row work here means there is exactly one place that knows how a
 * [GeneratedProgram] becomes rows.
 */
@Singleton
class GeneratedProgramWriter @Inject constructor(
    private val programDao: TrainingProgramDao,
    private val exerciseDao: ExerciseDao,
) {

    /**
     * Inserts [program] as the active program of [cycleId], with its weeks, one workout
     * template per training day, the exercises of each day, every planned set, and the
     * progress pointer aimed at the first unit. Returns the new program id.
     *
     * Sets are stored as individual rows rather than as a "3x3x175" scheme string, so a
     * session can snapshot each one on its own and the lifter can deviate on any of them.
     */
    suspend fun write(program: GeneratedProgram, cycleId: Long, createdAt: Instant): Long {
        val exerciseIds = program.requiredExercises.associate { it.name to resolveExerciseId(it) }

        val programId = programDao.insertProgram(
            TrainingProgramEntity(
                cycleId = cycleId,
                name = program.name,
                createdAt = createdAt,
                isActive = true,
            ),
        )

        var sequenceNumber = 0
        var firstTemplateId: Long? = null

        program.weeks.sortedBy { it.weekNumber }.forEach { week ->
            val weekId = programDao.insertWeek(
                TrainingWeekEntity(
                    programId = programId,
                    weekNumber = week.weekNumber,
                    label = week.label,
                    isDeload = week.isDeload,
                ),
            )

            week.days.sortedBy { it.dayNumber }.forEach { day ->
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

                day.exercises.sortedBy { it.position }.forEach { exercise ->
                    val exerciseTemplateId = programDao.insertExerciseTemplate(
                        ExerciseTemplateEntity(
                            workoutTemplateId = templateId,
                            exerciseId = exerciseIds.getValue(exercise.exerciseName),
                            type = exercise.type,
                            position = exercise.position,
                        ),
                    )
                    programDao.insertPlannedSets(
                        exercise.sets.map { set ->
                            PlannedSetEntity(
                                exerciseTemplateId = exerciseTemplateId,
                                position = set.position,
                                weightKg = set.weight?.kilograms,
                                reps = set.reps,
                                rpeMin = set.targetRpe?.min?.value,
                                rpeMax = set.targetRpe?.max?.value,
                            )
                        },
                    )
                }
            }
        }

        programDao.upsertProgress(
            ProgramProgressEntity(programId = programId, currentWorkoutTemplateId = firstTemplateId),
        )

        return programId
    }

    /**
     * The catalogue is normally already seeded; inserting what is missing keeps cycle
     * creation independent of when seeding happened to finish.
     */
    private suspend fun resolveExerciseId(exercise: TemplateExercise): Long =
        exerciseDao.getByName(exercise.name)?.id
            ?: exerciseDao.insert(ExerciseEntity(name = exercise.name, category = exercise.category))
}
