package com.griffgym.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseType
import java.time.Instant

@Entity(
    tableName = "exercise",
    indices = [Index(value = ["name"], unique = true), Index("category")],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: ExerciseCategory,
)

@Entity(tableName = "training_program")
data class TrainingProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Instant,
    val isActive: Boolean,
)

@Entity(
    tableName = "training_week",
    foreignKeys = [
        ForeignKey(
            entity = TrainingProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["programId", "weekNumber"], unique = true)],
)
data class TrainingWeekEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val weekNumber: Int,
    val label: String,
    val isDeload: Boolean,
)

@Entity(
    tableName = "workout_template",
    foreignKeys = [
        ForeignKey(
            entity = TrainingWeekEntity::class,
            parentColumns = ["id"],
            childColumns = ["weekId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["weekId", "dayNumber"], unique = true),
        Index(value = ["sequenceNumber"]),
    ],
)
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekId: Long,
    val dayNumber: Int,
    /** Position of this unit in the whole program. The plan is a sequence, not a calendar. */
    val sequenceNumber: Int,
    val title: String,
)

@Entity(
    tableName = "exercise_template",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutTemplateId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("workoutTemplateId"), Index("exerciseId")],
)
data class ExerciseTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutTemplateId: Long,
    val exerciseId: Long,
    val type: ExerciseType,
    val position: Int,
)

@Entity(
    tableName = "planned_set",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseTemplateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseTemplateId")],
)
data class PlannedSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseTemplateId: Long,
    val position: Int,
    /** Null for accessory work, where the plan prescribes reps and RPE but not a load. */
    val weightKg: Double?,
    val reps: Int?,
    val rpeMin: Double?,
    val rpeMax: Double?,
)

/**
 * Where the lifter currently is inside the program.
 *
 * Kept in its own table rather than on the program row so that advancing the plan never
 * rewrites the plan itself.
 */
@Entity(
    tableName = "program_progress",
    foreignKeys = [
        ForeignKey(
            entity = TrainingProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["currentWorkoutTemplateId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("currentWorkoutTemplateId")],
)
data class ProgramProgressEntity(
    @PrimaryKey val programId: Long,
    /** Null once every unit of the program has been completed. */
    val currentWorkoutTemplateId: Long?,
)

@Entity(tableName = "reference_max")
data class ReferenceMaxEntity(
    @PrimaryKey val category: ExerciseCategory,
    val weightKg: Double,
    val updatedOn: Long,
)
