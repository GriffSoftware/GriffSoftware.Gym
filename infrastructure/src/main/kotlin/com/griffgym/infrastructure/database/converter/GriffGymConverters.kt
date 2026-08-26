package com.griffgym.infrastructure.database.converter

import androidx.room.TypeConverter
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.WorkoutStatus
import java.time.Instant
import java.time.LocalDate

/**
 * Enums are stored by name and dates as primitives, so the schema stays readable in a
 * database browser and survives reordering of enum constants.
 */
class GriffGymConverters {

    @TypeConverter
    fun exerciseCategoryToString(value: ExerciseCategory): String = value.name

    @TypeConverter
    fun stringToExerciseCategory(value: String): ExerciseCategory = ExerciseCategory.valueOf(value)

    @TypeConverter
    fun exerciseTypeToString(value: ExerciseType): String = value.name

    @TypeConverter
    fun stringToExerciseType(value: String): ExerciseType = ExerciseType.valueOf(value)

    @TypeConverter
    fun workoutStatusToString(value: WorkoutStatus): String = value.name

    @TypeConverter
    fun stringToWorkoutStatus(value: String): WorkoutStatus = WorkoutStatus.valueOf(value)

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
