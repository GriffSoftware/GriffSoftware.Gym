package com.griffgym.domain.model

/** Which lift a movement belongs to. Only the big three feed statistics and reference maxes. */
enum class ExerciseCategory {
    SQUAT,
    DEADLIFT,
    BENCH_PRESS,
    ACCESSORY;

    val isBigThree: Boolean get() = this != ACCESSORY

    companion object {
        val bigThree: List<ExerciseCategory> = listOf(SQUAT, DEADLIFT, BENCH_PRESS)
    }
}

/** The role a movement plays inside a single training day. */
enum class ExerciseType {
    TOP,
    BACK_OFF,
    VOLUME,
    LIGHT,
    DELOAD,
    ACCESSORY;

    /** Only main-lift work counts towards strength progression statistics. */
    val isMainLift: Boolean get() = this != ACCESSORY
}

data class Exercise(
    val id: Long,
    val name: String,
    val category: ExerciseCategory,
) {
    val isBigThree: Boolean get() = category.isBigThree
}
