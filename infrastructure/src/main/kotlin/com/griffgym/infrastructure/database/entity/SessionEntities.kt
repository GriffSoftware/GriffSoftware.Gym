package com.griffgym.infrastructure.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.WorkoutStatus
import java.time.Instant

/**
 * A performed workout.
 *
 * Week, day, title and deload flag are copied in rather than read through [templateId]:
 * this row is a snapshot, and editing the program must never rewrite history. The link
 * back to the template is kept for provenance only and is nulled if the template is
 * ever removed.
 */
@Entity(
    tableName = "workout_session",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("templateId"), Index("status"), Index("date"), Index(value = ["syncId"], unique = true)],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Stable, server-shared identity. Minted on this device the moment the row is created,
     * so a record that has never been online already knows what the server will call it.
     */
    @ColumnInfo(defaultValue = "") val syncId: String = newSyncId(),
    val templateId: Long?,
    val weekNumber: Int,
    val dayNumber: Int,
    val title: String,
    val isDeload: Boolean,
    val status: WorkoutStatus,
    val date: Long,
    val startedAt: Instant,
    val finishedAt: Instant?,
    /** Tonnage frozen at completion time; recomputed from sets while the session is live. */
    val totalVolumeKg: Double?,
    val notes: String?,
)

@Entity(
    tableName = "exercise_log",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId"), Index(value = ["syncId"], unique = true)],
)
data class ExerciseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Stable, server-shared identity. Minted on this device the moment the row is created,
     * so a record that has never been online already knows what the server will call it.
     */
    @ColumnInfo(defaultValue = "") val syncId: String = newSyncId(),
    val sessionId: Long,
    val exerciseId: Long,
    val type: ExerciseType,
    val position: Int,
)

@Entity(
    tableName = "set_log",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseLogId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseLogId"), Index(value = ["syncId"], unique = true)],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Stable, server-shared identity. Minted on this device the moment the row is created,
     * so a record that has never been online already knows what the server will call it.
     */
    @ColumnInfo(defaultValue = "") val syncId: String = newSyncId(),
    val exerciseLogId: Long,
    val position: Int,
    val plannedWeightKg: Double?,
    val plannedReps: Int?,
    val plannedRpeMin: Double?,
    val plannedRpeMax: Double?,
    val actualWeightKg: Double?,
    val actualReps: Int?,
    val actualRpe: Double?,
    val completed: Boolean,
    val notes: String?,
)
