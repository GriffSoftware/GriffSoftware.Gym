package com.griffgym.infrastructure.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.griffgym.infrastructure.database.entity.ExerciseEntity
import com.griffgym.infrastructure.database.entity.ExerciseLogEntity
import com.griffgym.infrastructure.database.entity.ExerciseTemplateEntity
import com.griffgym.infrastructure.database.entity.PlannedSetEntity
import com.griffgym.infrastructure.database.entity.SetLogEntity
import com.griffgym.infrastructure.database.entity.TrainingProgramEntity
import com.griffgym.infrastructure.database.entity.TrainingWeekEntity
import com.griffgym.infrastructure.database.entity.WorkoutSessionEntity
import com.griffgym.infrastructure.database.entity.WorkoutTemplateEntity

data class ExerciseTemplateWithDetails(
    @Embedded val template: ExerciseTemplateEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseTemplateId")
    val plannedSets: List<PlannedSetEntity>,
)

data class WorkoutTemplateWithExercises(
    @Embedded val template: WorkoutTemplateEntity,
    @Relation(
        entity = ExerciseTemplateEntity::class,
        parentColumn = "id",
        entityColumn = "workoutTemplateId",
    )
    val exercises: List<ExerciseTemplateWithDetails>,
)

/**
 * A single template together with the week it belongs to.
 *
 * The two week columns are joined in rather than duplicated on `workout_template`, which
 * keeps the schema normalised while still giving the UI everything it needs in one read.
 */
data class WorkoutTemplateDetail(
    @Embedded val template: WorkoutTemplateEntity,
    val weekNumber: Int,
    val isDeload: Boolean,
    @Relation(
        entity = ExerciseTemplateEntity::class,
        parentColumn = "id",
        entityColumn = "workoutTemplateId",
    )
    val exercises: List<ExerciseTemplateWithDetails>,
)

data class TrainingWeekWithWorkouts(
    @Embedded val week: TrainingWeekEntity,
    @Relation(
        entity = WorkoutTemplateEntity::class,
        parentColumn = "id",
        entityColumn = "weekId",
    )
    val workouts: List<WorkoutTemplateWithExercises>,
)

data class TrainingProgramWithWeeks(
    @Embedded val program: TrainingProgramEntity,
    @Relation(
        entity = TrainingWeekEntity::class,
        parentColumn = "id",
        entityColumn = "programId",
    )
    val weeks: List<TrainingWeekWithWorkouts>,
)

data class ExerciseLogWithDetails(
    @Embedded val log: ExerciseLogEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseLogId")
    val sets: List<SetLogEntity>,
)

data class WorkoutSessionWithExercises(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        entity = ExerciseLogEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val exercises: List<ExerciseLogWithDetails>,
)
