package com.griffgym.infrastructure.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The wire spellings of the API's enums.
 *
 * The server serialises C# enum *names*, so `Top`, `BackOff` and `InProgress` are the literal
 * strings on the wire. They are pinned here with @SerialName rather than by naming the Kotlin
 * constants to match, so that the constants can stay UPPER_SNAKE like the rest of the codebase
 * and a rename on either side becomes a compile error instead of a silent parse failure.
 *
 * These are network types, not domain ones. The domain has its own enums with their own
 * spellings, and mapping between them is a mapper's job — coupling the two would mean a server
 * enum could never be renamed without a Room migration.
 */

@Serializable
internal enum class LiftTypeDto {
    @SerialName("Squat")
    SQUAT,

    @SerialName("BenchPress")
    BENCH_PRESS,

    @SerialName("Deadlift")
    DEADLIFT,
    ;

    /** The `{lift}` route segment on `PUT /api/v1/reference-maxes/{lift}`. */
    val routeValue: String
        get() = when (this) {
            SQUAT -> "Squat"
            BENCH_PRESS -> "BenchPress"
            DEADLIFT -> "Deadlift"
        }
}

@Serializable
internal enum class ExerciseCategoryDto {
    @SerialName("Squat")
    SQUAT,

    @SerialName("BenchPress")
    BENCH_PRESS,

    @SerialName("Deadlift")
    DEADLIFT,

    @SerialName("Accessory")
    ACCESSORY,
}

@Serializable
internal enum class ExerciseTypeDto {
    @SerialName("Top")
    TOP,

    @SerialName("BackOff")
    BACK_OFF,

    @SerialName("Volume")
    VOLUME,

    @SerialName("Light")
    LIGHT,

    @SerialName("Deload")
    DELOAD,

    @SerialName("Accessory")
    ACCESSORY,
}

@Serializable
internal enum class TrainingCycleStatusDto {
    @SerialName("Active")
    ACTIVE,

    @SerialName("Completed")
    COMPLETED,
}

@Serializable
internal enum class TrainingWeekTypeDto {
    @SerialName("Training")
    TRAINING,

    @SerialName("Deload")
    DELOAD,
}

@Serializable
internal enum class WorkoutSessionStatusDto {
    @SerialName("InProgress")
    IN_PROGRESS,

    @SerialName("Completed")
    COMPLETED,

    @SerialName("Cancelled")
    CANCELLED,
    ;

    /** The `status` query parameter on `GET /api/v1/workouts`. */
    val queryValue: String
        get() = when (this) {
            IN_PROGRESS -> "InProgress"
            COMPLETED -> "Completed"
            CANCELLED -> "Cancelled"
        }
}
