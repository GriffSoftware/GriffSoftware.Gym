package com.griffgym.infrastructure.mapper

import com.griffgym.domain.model.CycleWeekProgress
import com.griffgym.domain.model.Exercise
import com.griffgym.domain.model.ExerciseTemplate
import com.griffgym.domain.model.PlannedSet
import com.griffgym.domain.model.ReferenceMax
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.Rpe
import com.griffgym.domain.model.RpeTarget
import com.griffgym.domain.model.TrainingCycle
import com.griffgym.domain.model.TrainingProgram
import com.griffgym.domain.model.TrainingWeek
import com.griffgym.domain.model.Weight
import com.griffgym.domain.model.WorkoutTemplate
import com.griffgym.infrastructure.database.entity.ExerciseEntity
import com.griffgym.infrastructure.database.entity.PlannedSetEntity
import com.griffgym.infrastructure.database.entity.ReferenceMaxEntity
import com.griffgym.infrastructure.database.entity.TrainingCycleEntity
import com.griffgym.infrastructure.database.relation.CycleWeekProgressRow
import com.griffgym.infrastructure.database.relation.ExerciseTemplateWithDetails
import com.griffgym.infrastructure.database.relation.TrainingProgramWithWeeks
import com.griffgym.infrastructure.database.relation.TrainingWeekWithWorkouts
import com.griffgym.infrastructure.database.relation.WorkoutTemplateDetail
import com.griffgym.infrastructure.database.relation.WorkoutTemplateWithExercises
import java.time.LocalDate

/*
 * Room entities never leave this module. Everything above infrastructure sees domain
 * models only, which is what lets the persistence layer be replaced without touching a
 * single use case.
 *
 * Room does not guarantee the order of @Relation collections, so every list is sorted by
 * its explicit position column here.
 */

internal fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    name = name,
    category = category,
)

internal fun PlannedSetEntity.toDomain(): PlannedSet = PlannedSet(
    id = id,
    position = position,
    weight = weightKg?.let(Weight::of),
    reps = reps,
    targetRpe = toRpeTarget(rpeMin, rpeMax),
)

internal fun ExerciseTemplateWithDetails.toDomain(): ExerciseTemplate = ExerciseTemplate(
    id = template.id,
    position = template.position,
    exercise = exercise.toDomain(),
    type = template.type,
    plannedSets = plannedSets.sortedBy { it.position }.map { it.toDomain() },
)

internal fun WorkoutTemplateDetail.toDomain(): WorkoutTemplate = WorkoutTemplate(
    id = template.id,
    weekId = template.weekId,
    weekNumber = weekNumber,
    dayNumber = template.dayNumber,
    sequenceNumber = template.sequenceNumber,
    title = template.title,
    isDeload = isDeload,
    exercises = exercises.sortedBy { it.template.position }.map { it.toDomain() },
)

internal fun WorkoutTemplateWithExercises.toDomain(
    weekNumber: Int,
    isDeload: Boolean,
): WorkoutTemplate = WorkoutTemplate(
    id = template.id,
    weekId = template.weekId,
    weekNumber = weekNumber,
    dayNumber = template.dayNumber,
    sequenceNumber = template.sequenceNumber,
    title = template.title,
    isDeload = isDeload,
    exercises = exercises.sortedBy { it.template.position }.map { it.toDomain() },
)

internal fun TrainingWeekWithWorkouts.toDomain(): TrainingWeek = TrainingWeek(
    id = week.id,
    programId = week.programId,
    weekNumber = week.weekNumber,
    label = week.label,
    isDeload = week.isDeload,
    workouts = workouts
        .sortedBy { it.template.dayNumber }
        .map { it.toDomain(week.weekNumber, week.isDeload) },
)

internal fun TrainingProgramWithWeeks.toDomain(): TrainingProgram = TrainingProgram(
    id = program.id,
    name = program.name,
    weeks = weeks.sortedBy { it.week.weekNumber }.map { it.toDomain() },
)

internal fun TrainingCycleEntity.toDomain(): TrainingCycle = TrainingCycle(
    id = id,
    cycleNumber = cycleNumber,
    status = status,
    startedAt = startedAt,
    completedAt = completedAt,
    referenceMaxes = ReferenceMaxSnapshot(
        squat = Weight.of(squatKg),
        benchPress = Weight.of(benchPressKg),
        deadlift = Weight.of(deadliftKg),
    ),
    createdAt = createdAt,
)

internal fun CycleWeekProgressRow.toDomain(): CycleWeekProgress = CycleWeekProgress(
    weekNumber = weekNumber,
    label = label,
    isDeload = isDeload,
    plannedWorkouts = plannedWorkouts,
    completedWorkouts = completedWorkouts,
)

internal fun ReferenceMaxSnapshot.toReferenceMaxes(updatedOn: LocalDate): List<ReferenceMax> =
    byCategory.map { (category, weight) -> ReferenceMax(category, weight, updatedOn) }

internal fun ReferenceMaxEntity.toDomain(): ReferenceMax = ReferenceMax(
    category = category,
    weight = Weight.of(weightKg),
    updatedOn = LocalDate.ofEpochDay(updatedOn),
)

internal fun ReferenceMax.toEntity(): ReferenceMaxEntity = ReferenceMaxEntity(
    category = category,
    weightKg = weight.kilograms,
    updatedOn = updatedOn.toEpochDay(),
)

internal fun toRpeTarget(min: Double?, max: Double?): RpeTarget? {
    val lower = min?.let(Rpe::ofOrNull) ?: return null
    val upper = max?.let(Rpe::ofOrNull) ?: lower
    return RpeTarget(lower, upper)
}
