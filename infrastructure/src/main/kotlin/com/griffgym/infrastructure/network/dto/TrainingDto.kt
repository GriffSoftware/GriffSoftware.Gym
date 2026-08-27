package com.griffgym.infrastructure.network.dto

import kotlinx.serialization.Serializable
import java.time.Instant

/*
 * Reference maxes, the movement catalogue and cycles. Mirrors
 * GriffGym.Api.Contracts.V1.TrainingContracts.
 *
 * Weights and RPE are `decimal` on the server and Double here. Not Float: a Float carries
 * about seven significant digits, and this program moves in 1.25 kg steps — 117.5, 132.5,
 * 142.5, 162.5 — that a lifter reads back years later. Double round-trips those exactly at
 * the magnitudes involved; Float is one careless conversion away from 142.49999.
 *
 * Identifiers are Strings holding GUIDs rather than a parsed UUID type: the phone owns them,
 * the server stores whatever it is sent, and nothing in this layer needs to do arithmetic on
 * them. Parsing would only add a failure mode.
 */

@Serializable
internal data class ReferenceMaxResponseDto(
    val id: String,
    val lift: LiftTypeDto,
    val valueKg: Double,
    @Serializable(with = InstantSerializer::class)
    val createdAtUtc: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAtUtc: Instant,
    val version: Int,
    val syncVersion: Long,
)

/**
 * The lift comes from the route and is deliberately absent here: a body that could name a
 * lift would let a request claim to update the squat while carrying a bench payload.
 */
@Serializable
internal data class UpdateReferenceMaxRequestDto(
    val valueKg: Double,
    val id: String? = null,
)

@Serializable
internal data class ExerciseResponseDto(
    val id: String,
    val name: String,
    val category: ExerciseCategoryDto,
    @Serializable(with = InstantSerializer::class)
    val createdAtUtc: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAtUtc: Instant,
    val version: Int,
    val syncVersion: Long,
)

@Serializable
internal data class ExerciseRequestDto(
    val id: String,
    val name: String,
    val category: ExerciseCategoryDto,
)

// ---------------------------------------------------------------------------------------------
// Cycles
// ---------------------------------------------------------------------------------------------

/**
 * A whole cycle in one request: the planning numbers it was built from, the movements its plan
 * refers to, and all six weeks.
 *
 * [exercises] is not ceremony. Templates reference movements by id, and carrying the catalogue
 * on the plan means uploading a cycle never depends on the server having been seeded first —
 * which matters because the phone generates the block offline and may be uploading it into a
 * brand new account.
 */
@Serializable
internal data class CreateCycleRequestDto(
    val id: String? = null,
    val cycleNumber: Int,
    val squatReferenceMaxKg: Double,
    val benchPressReferenceMaxKg: Double,
    val deadliftReferenceMaxKg: Double,
    @Serializable(with = InstantSerializer::class)
    val startedAtUtc: Instant,
    val exercises: List<ExerciseRequestDto>,
    val program: ProgramRequestDto,
)

@Serializable
internal data class ProgramRequestDto(
    val id: String? = null,
    val name: String,
    val currentWorkoutTemplateId: String? = null,
    val weeks: List<WeekRequestDto>,
)

@Serializable
internal data class WeekRequestDto(
    val id: String? = null,
    val weekNumber: Int,
    val label: String,
    val type: TrainingWeekTypeDto,
    val workouts: List<WorkoutTemplateRequestDto>,
)

@Serializable
internal data class WorkoutTemplateRequestDto(
    val id: String? = null,
    val dayNumber: Int,
    val sequenceNumber: Int,
    val title: String,
    val exercises: List<ExerciseTemplateRequestDto>,
)

@Serializable
internal data class ExerciseTemplateRequestDto(
    val id: String? = null,
    val position: Int,
    val exerciseId: String,
    val exerciseName: String? = null,
    val exerciseCategory: ExerciseCategoryDto? = null,
    val type: ExerciseTypeDto,
    val plannedSets: List<PlannedSetRequestDto>,
)

/**
 * Every prescribed value is nullable because an accessory can legitimately be planned without
 * one — "chin-ups, 3 sets to failure" has no weight and no rep target, and forcing a zero in
 * would make it indistinguishable from a bar with nothing on it.
 */
@Serializable
internal data class PlannedSetRequestDto(
    val id: String? = null,
    val position: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val rpeMin: Double? = null,
    val rpeMax: Double? = null,
)

@Serializable
internal data class CompleteCycleRequestDto(
    @Serializable(with = InstantSerializer::class)
    val completedAtUtc: Instant? = null,
)

/**
 * Moves the plan's pointer. A null [currentWorkoutTemplateId] clears it, which is what
 * finishing the last unit of a block means.
 */
@Serializable
internal data class UpdateCycleProgressRequestDto(
    val currentWorkoutTemplateId: String? = null,
)

@Serializable
internal data class ReferenceMaxSnapshotResponseDto(
    val squatKg: Double,
    val benchPressKg: Double,
    val deadliftKg: Double,
)

@Serializable
internal data class CycleResponseDto(
    val id: String,
    val cycleNumber: Int,
    val status: TrainingCycleStatusDto,
    val referenceMaxes: ReferenceMaxSnapshotResponseDto,
    @Serializable(with = InstantSerializer::class)
    val startedAtUtc: Instant,
    @Serializable(with = InstantSerializer::class)
    val completedAtUtc: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAtUtc: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAtUtc: Instant,
    val version: Int,
    val syncVersion: Long,
    val program: ProgramResponseDto,
)

@Serializable
internal data class ProgramResponseDto(
    val id: String,
    val name: String,
    val currentWorkoutTemplateId: String? = null,
    val weeks: List<WeekResponseDto>,
)

@Serializable
internal data class WeekResponseDto(
    val id: String,
    val weekNumber: Int,
    val label: String,
    val type: TrainingWeekTypeDto,
    val isDeload: Boolean,
    val workouts: List<WorkoutTemplateResponseDto>,
)

@Serializable
internal data class WorkoutTemplateResponseDto(
    val id: String,
    val dayNumber: Int,
    val sequenceNumber: Int,
    val title: String,
    val exercises: List<ExerciseTemplateResponseDto>,
)

@Serializable
internal data class ExerciseTemplateResponseDto(
    val id: String,
    val position: Int,
    val exerciseId: String,
    val exerciseName: String,
    val exerciseCategory: ExerciseCategoryDto,
    val type: ExerciseTypeDto,
    val plannedSets: List<PlannedSetResponseDto>,
)

@Serializable
internal data class PlannedSetResponseDto(
    val id: String,
    val position: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val rpeMin: Double? = null,
    val rpeMax: Double? = null,
)

/**
 * A cycle without its plan, for a list screen. The server counts progress from completed
 * sessions rather than tracking it separately, so it cannot drift away from the training log.
 */
@Serializable
internal data class CycleSummaryResponseDto(
    val id: String,
    val cycleNumber: Int,
    val status: TrainingCycleStatusDto,
    val referenceMaxes: ReferenceMaxSnapshotResponseDto,
    @Serializable(with = InstantSerializer::class)
    val startedAtUtc: Instant,
    @Serializable(with = InstantSerializer::class)
    val completedAtUtc: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAtUtc: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAtUtc: Instant,
    val version: Int,
    val syncVersion: Long,
    val programId: String,
    val programName: String,
    val currentWorkoutTemplateId: String? = null,
    val plannedWorkouts: Int,
    val completedWorkouts: Int,
    val completedWeeks: Int,
    val currentWeekNumber: Int? = null,
    val weeks: List<CycleWeekProgressResponseDto>,
)

@Serializable
internal data class CycleWeekProgressResponseDto(
    val id: String,
    val weekNumber: Int,
    val label: String,
    val isDeload: Boolean,
    val plannedWorkouts: Int,
    val completedWorkouts: Int,
    val isComplete: Boolean,
    val isStarted: Boolean,
)
