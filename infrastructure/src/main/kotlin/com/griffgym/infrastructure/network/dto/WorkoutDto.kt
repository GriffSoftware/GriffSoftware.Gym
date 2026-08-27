package com.griffgym.infrastructure.network.dto

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/*
 * Workout sessions. Mirrors GriffGym.Api.Contracts.V1.WorkoutContracts.
 */

/**
 * Creates a session, either way round.
 *
 * With [trainingCycleId] and [workoutTemplateId] and no [exercises], the server snapshots the
 * planned unit out of the cycle — the ordinary "press START" path. With [exercises] supplied,
 * the client is uploading a session it already holds: one started offline, or one out of the
 * history accumulated before there was an account to sync to. Sending neither is a 400.
 *
 * There is no user id here or anywhere else in this API. Ownership comes from the access
 * token and cannot be asserted by a request.
 */
@Serializable
internal data class CreateWorkoutRequestDto(
    val id: String? = null,
    val trainingCycleId: String? = null,
    val trainingWeekId: String? = null,
    val workoutTemplateId: String? = null,
    val weekNumber: Int? = null,
    val dayNumber: Int? = null,
    val title: String? = null,
    val isDeload: Boolean? = null,
    val status: WorkoutSessionStatusDto? = null,
    @Serializable(with = LocalDateSerializer::class)
    val performedOn: LocalDate? = null,
    @Serializable(with = InstantSerializer::class)
    val startedAtUtc: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val finishedAtUtc: Instant? = null,
    val notes: String? = null,
    val exercises: List<ExerciseLogRequestDto>? = null,
)

@Serializable
internal data class ExerciseLogRequestDto(
    val id: String? = null,
    val position: Int,
    val exerciseId: String? = null,
    val exerciseName: String? = null,
    val exerciseCategory: ExerciseCategoryDto? = null,
    val type: ExerciseTypeDto,
    val notes: String? = null,
    val sets: List<SetLogRequestDto>,
)

/**
 * Planned and actual, side by side and never merged, because editing a plan later must not be
 * able to rewrite what was lifted.
 */
@Serializable
internal data class SetLogRequestDto(
    val id: String? = null,
    val position: Int,
    val plannedWeightKg: Double? = null,
    val plannedReps: Int? = null,
    val plannedRpeMin: Double? = null,
    val plannedRpeMax: Double? = null,
    val actualWeightKg: Double? = null,
    val actualReps: Int? = null,
    val actualRpe: Double? = null,
    val completed: Boolean,
    val notes: String? = null,
)

/**
 * [expectedVersion] is the revision the client believes it holds. Sending it turns a blind
 * overwrite into a detected 409; omitting it is last-write-wins, which is only ever right for
 * a device that knows it is the only one writing. Use the `version` from the previous
 * response — never a number this app incremented itself.
 */
@Serializable
internal data class UpdateWorkoutRequestDto(
    val expectedVersion: Int? = null,
    val notes: String? = null,
    val exercises: List<ExerciseLogRequestDto>? = null,
)

@Serializable
internal data class LogSetRequestDto(
    val expectedVersion: Int? = null,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val rpe: Double? = null,
    val completed: Boolean,
    val notes: String? = null,
)

/** Shared by `complete` and `cancel`: both finish a session, only one of them counts. */
@Serializable
internal data class FinishWorkoutRequestDto(
    val expectedVersion: Int? = null,
    @Serializable(with = InstantSerializer::class)
    val finishedAtUtc: Instant? = null,
)

@Serializable
internal data class WorkoutResponseDto(
    val id: String,
    val trainingCycleId: String? = null,
    val trainingWeekId: String? = null,
    val workoutTemplateId: String? = null,
    val weekNumber: Int,
    val dayNumber: Int,
    val title: String,
    val isDeload: Boolean,
    val status: WorkoutSessionStatusDto,
    @Serializable(with = LocalDateSerializer::class)
    val performedOn: LocalDate,
    @Serializable(with = InstantSerializer::class)
    val startedAtUtc: Instant,
    @Serializable(with = InstantSerializer::class)
    val finishedAtUtc: Instant? = null,
    val durationSeconds: Long? = null,
    val totalVolumeKg: Double,
    val totalSets: Int,
    val completedSets: Int,
    val totalReps: Int,
    val notes: String? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAtUtc: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAtUtc: Instant,
    val version: Int,
    val syncVersion: Long,
    val exercises: List<ExerciseLogResponseDto>,
)

/** A session without its sets. What the paginated history returns. */
@Serializable
internal data class WorkoutSummaryResponseDto(
    val id: String,
    val trainingCycleId: String? = null,
    val weekNumber: Int,
    val dayNumber: Int,
    val title: String,
    val isDeload: Boolean,
    val status: WorkoutSessionStatusDto,
    @Serializable(with = LocalDateSerializer::class)
    val performedOn: LocalDate,
    @Serializable(with = InstantSerializer::class)
    val startedAtUtc: Instant,
    @Serializable(with = InstantSerializer::class)
    val finishedAtUtc: Instant? = null,
    val durationSeconds: Long? = null,
    val totalVolumeKg: Double,
    val totalSets: Int,
    val completedSets: Int,
    val totalReps: Int,
    @Serializable(with = InstantSerializer::class)
    val updatedAtUtc: Instant,
    val version: Int,
    val syncVersion: Long,
)

@Serializable
internal data class ExerciseLogResponseDto(
    val id: String,
    val position: Int,
    val exerciseId: String? = null,
    val exerciseName: String,
    val exerciseCategory: ExerciseCategoryDto,
    val type: ExerciseTypeDto,
    val notes: String? = null,
    val volumeKg: Double,
    val bestEstimatedOneRepMaxKg: Double? = null,
    val sets: List<SetLogResponseDto>,
)

@Serializable
internal data class SetLogResponseDto(
    val id: String,
    val position: Int,
    val plannedWeightKg: Double? = null,
    val plannedReps: Int? = null,
    val plannedRpeMin: Double? = null,
    val plannedRpeMax: Double? = null,
    val actualWeightKg: Double? = null,
    val actualReps: Int? = null,
    val actualRpe: Double? = null,
    val completed: Boolean,
    val notes: String? = null,
    val volumeKg: Double,
    val estimatedOneRepMaxKg: Double? = null,
)

/** One page of results, with enough context to ask for the next. */
@Serializable
internal data class PagedResponseDto<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long,
    val totalPages: Int,
    val hasNextPage: Boolean,
)
