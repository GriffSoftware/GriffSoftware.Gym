package com.griffgym.infrastructure.network.dto

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Everything one lifter's installation is made of, in one read-only document.
 *
 * The answer to "my phone is in a river": a fresh install signs in, asks once, and rebuilds
 * its local database from this — the planning numbers, every cycle with the plan it was
 * actually trained on, where the lifter is inside the current plan, every logged session with
 * its planned and actual sets, and the workout still open.
 *
 * [schemaVersion] describes this document, not the database. It is read and checked against
 * [SUPPORTED_SCHEMA_VERSION] before a restore, so an old build refuses a document it does not
 * understand rather than restoring something it half recognises and calling it history.
 */
@Serializable
internal data class ApplicationStateResponseDto(
    val schemaVersion: Int,
    @Serializable(with = InstantSerializer::class)
    val generatedAtUtc: Instant,
    val syncVersion: Long,
    val profile: UserResponseDto,
    val referenceMaxes: List<ReferenceMaxResponseDto>,
    val exercises: List<ExerciseResponseDto>,
    val cycles: List<CycleResponseDto>,
    val currentCycleId: String? = null,
    val activeWorkoutId: String? = null,
    val workouts: List<WorkoutResponseDto>,
) {
    companion object {
        /** The highest `schemaVersion` this build knows how to restore. */
        const val SUPPORTED_SCHEMA_VERSION: Int = 1
    }
}
