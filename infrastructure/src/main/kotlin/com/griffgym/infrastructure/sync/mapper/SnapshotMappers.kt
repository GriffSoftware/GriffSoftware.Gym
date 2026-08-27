package com.griffgym.infrastructure.sync.mapper

import com.griffgym.domain.model.CycleStatus
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.WorkoutStatus
import com.griffgym.infrastructure.network.dto.ApplicationStateResponseDto
import com.griffgym.infrastructure.network.dto.CreateCycleRequestDto
import com.griffgym.infrastructure.network.dto.CreateWorkoutRequestDto
import com.griffgym.infrastructure.network.dto.CycleResponseDto
import com.griffgym.infrastructure.network.dto.ExerciseCategoryDto
import com.griffgym.infrastructure.network.dto.ExerciseLogRequestDto
import com.griffgym.infrastructure.network.dto.ExerciseLogResponseDto
import com.griffgym.infrastructure.network.dto.ExerciseRequestDto
import com.griffgym.infrastructure.network.dto.ExerciseResponseDto
import com.griffgym.infrastructure.network.dto.ExerciseTemplateRequestDto
import com.griffgym.infrastructure.network.dto.ExerciseTypeDto
import com.griffgym.infrastructure.network.dto.LiftTypeDto
import com.griffgym.infrastructure.network.dto.PlannedSetRequestDto
import com.griffgym.infrastructure.network.dto.ProgramRequestDto
import com.griffgym.infrastructure.network.dto.ReferenceMaxResponseDto
import com.griffgym.infrastructure.network.dto.SetLogRequestDto
import com.griffgym.infrastructure.network.dto.SetLogResponseDto
import com.griffgym.infrastructure.network.dto.TrainingCycleStatusDto
import com.griffgym.infrastructure.network.dto.TrainingWeekTypeDto
import com.griffgym.infrastructure.network.dto.WeekRequestDto
import com.griffgym.infrastructure.network.dto.WorkoutResponseDto
import com.griffgym.infrastructure.network.dto.WorkoutSessionStatusDto
import com.griffgym.infrastructure.network.dto.WorkoutTemplateRequestDto
import com.griffgym.infrastructure.sync.model.CloudSnapshot
import com.griffgym.infrastructure.sync.model.SnapshotCycle
import com.griffgym.infrastructure.sync.model.SnapshotExercise
import com.griffgym.infrastructure.sync.model.SnapshotExerciseLog
import com.griffgym.infrastructure.sync.model.SnapshotExerciseTemplate
import com.griffgym.infrastructure.sync.model.SnapshotPlannedSet
import com.griffgym.infrastructure.sync.model.SnapshotProgram
import com.griffgym.infrastructure.sync.model.SnapshotReferenceMax
import com.griffgym.infrastructure.sync.model.SnapshotSetLog
import com.griffgym.infrastructure.sync.model.SnapshotWeek
import com.griffgym.infrastructure.sync.model.SnapshotWorkout
import com.griffgym.infrastructure.sync.model.SnapshotWorkoutTemplate
import java.time.LocalDate
import java.time.ZoneOffset

/*
 * Wire shapes on one side, the snapshot the database understands on the other.
 *
 * The two vocabularies are kept apart deliberately. The server speaks `LiftType.BenchPress`
 * and the app speaks `ExerciseCategory.BENCH_PRESS`; letting either name leak into the other
 * would mean the server could never rename an enum without a Room migration, and the app could
 * never rename one without breaking its own backups.
 */

// --- categories and statuses -------------------------------------------------------------

internal fun ExerciseCategoryDto.toDomain(): ExerciseCategory = when (this) {
    ExerciseCategoryDto.SQUAT -> ExerciseCategory.SQUAT
    ExerciseCategoryDto.BENCH_PRESS -> ExerciseCategory.BENCH_PRESS
    ExerciseCategoryDto.DEADLIFT -> ExerciseCategory.DEADLIFT
    ExerciseCategoryDto.ACCESSORY -> ExerciseCategory.ACCESSORY
}

internal fun ExerciseCategory.toDto(): ExerciseCategoryDto = when (this) {
    ExerciseCategory.SQUAT -> ExerciseCategoryDto.SQUAT
    ExerciseCategory.BENCH_PRESS -> ExerciseCategoryDto.BENCH_PRESS
    ExerciseCategory.DEADLIFT -> ExerciseCategoryDto.DEADLIFT
    ExerciseCategory.ACCESSORY -> ExerciseCategoryDto.ACCESSORY
}

/**
 * The server files reference maxes under a lift, which has no accessory case — accessory work
 * has no max to plan from. A snapshot row for one would be a corrupt row, and is dropped
 * rather than sent as something the server would reject.
 */
internal fun ExerciseCategory.toLiftDtoOrNull(): LiftTypeDto? = when (this) {
    ExerciseCategory.SQUAT -> LiftTypeDto.SQUAT
    ExerciseCategory.BENCH_PRESS -> LiftTypeDto.BENCH_PRESS
    ExerciseCategory.DEADLIFT -> LiftTypeDto.DEADLIFT
    ExerciseCategory.ACCESSORY -> null
}

internal fun LiftTypeDto.toCategory(): ExerciseCategory = when (this) {
    LiftTypeDto.SQUAT -> ExerciseCategory.SQUAT
    LiftTypeDto.BENCH_PRESS -> ExerciseCategory.BENCH_PRESS
    LiftTypeDto.DEADLIFT -> ExerciseCategory.DEADLIFT
}

internal fun ExerciseTypeDto.toDomain(): ExerciseType = when (this) {
    ExerciseTypeDto.TOP -> ExerciseType.TOP
    ExerciseTypeDto.BACK_OFF -> ExerciseType.BACK_OFF
    ExerciseTypeDto.VOLUME -> ExerciseType.VOLUME
    ExerciseTypeDto.LIGHT -> ExerciseType.LIGHT
    ExerciseTypeDto.DELOAD -> ExerciseType.DELOAD
    ExerciseTypeDto.ACCESSORY -> ExerciseType.ACCESSORY
}

internal fun ExerciseType.toDto(): ExerciseTypeDto = when (this) {
    ExerciseType.TOP -> ExerciseTypeDto.TOP
    ExerciseType.BACK_OFF -> ExerciseTypeDto.BACK_OFF
    ExerciseType.VOLUME -> ExerciseTypeDto.VOLUME
    ExerciseType.LIGHT -> ExerciseTypeDto.LIGHT
    ExerciseType.DELOAD -> ExerciseTypeDto.DELOAD
    ExerciseType.ACCESSORY -> ExerciseTypeDto.ACCESSORY
}

internal fun TrainingCycleStatusDto.toDomain(): CycleStatus = when (this) {
    TrainingCycleStatusDto.ACTIVE -> CycleStatus.ACTIVE
    TrainingCycleStatusDto.COMPLETED -> CycleStatus.COMPLETED
}

internal fun WorkoutSessionStatusDto.toDomain(): WorkoutStatus = when (this) {
    WorkoutSessionStatusDto.IN_PROGRESS -> WorkoutStatus.IN_PROGRESS
    WorkoutSessionStatusDto.COMPLETED -> WorkoutStatus.COMPLETED
    WorkoutSessionStatusDto.CANCELLED -> WorkoutStatus.CANCELLED
}

internal fun WorkoutStatus.toDto(): WorkoutSessionStatusDto = when (this) {
    WorkoutStatus.IN_PROGRESS -> WorkoutSessionStatusDto.IN_PROGRESS
    WorkoutStatus.COMPLETED -> WorkoutSessionStatusDto.COMPLETED
    WorkoutStatus.CANCELLED -> WorkoutSessionStatusDto.CANCELLED
}

// --- server document -> snapshot ------------------------------------------------------------

internal fun ApplicationStateResponseDto.toSnapshot(): CloudSnapshot = CloudSnapshot(
    exercises = exercises.map(ExerciseResponseDto::toSnapshot),
    referenceMaxes = referenceMaxes.map(ReferenceMaxResponseDto::toSnapshot),
    // Oldest first. Cycle 1 must restore as cycle 1, and the writer inserts in this order.
    cycles = cycles.sortedBy { it.cycleNumber }.map(CycleResponseDto::toSnapshot),
    workouts = workouts.map(WorkoutResponseDto::toSnapshot),
)

private fun ExerciseResponseDto.toSnapshot() = SnapshotExercise(
    syncId = id,
    name = name,
    category = category.toDomain(),
)

private fun ReferenceMaxResponseDto.toSnapshot() = SnapshotReferenceMax(
    syncId = id,
    category = lift.toCategory(),
    weightKg = valueKg,
    // The local schema records the day a max was set, which the API models as the moment it
    // was last written. UTC, because that is the only reading that is the same everywhere.
    updatedOn = updatedAtUtc.atZone(ZoneOffset.UTC).toLocalDate(),
)

private fun CycleResponseDto.toSnapshot() = SnapshotCycle(
    syncId = id,
    cycleNumber = cycleNumber,
    status = status.toDomain(),
    startedAt = startedAtUtc,
    completedAt = completedAtUtc,
    squatKg = referenceMaxes.squatKg,
    benchPressKg = referenceMaxes.benchPressKg,
    deadliftKg = referenceMaxes.deadliftKg,
    createdAt = createdAtUtc,
    program = SnapshotProgram(
        syncId = program.id,
        name = program.name,
        createdAt = createdAtUtc,
        // Exactly one program is active: the one belonging to the cycle still being trained.
        isActive = status == TrainingCycleStatusDto.ACTIVE,
        currentWorkoutTemplateSyncId = program.currentWorkoutTemplateId,
        weeks = program.weeks.sortedBy { it.weekNumber }.map { week ->
            SnapshotWeek(
                syncId = week.id,
                weekNumber = week.weekNumber,
                label = week.label,
                isDeload = week.isDeload || week.type == TrainingWeekTypeDto.DELOAD,
                workouts = week.workouts.sortedBy { it.dayNumber }.map { template ->
                    SnapshotWorkoutTemplate(
                        syncId = template.id,
                        dayNumber = template.dayNumber,
                        sequenceNumber = template.sequenceNumber,
                        title = template.title,
                        exercises = template.exercises.sortedBy { it.position }.map { exercise ->
                            SnapshotExerciseTemplate(
                                syncId = exercise.id,
                                exerciseSyncId = exercise.exerciseId,
                                exerciseName = exercise.exerciseName,
                                exerciseCategory = exercise.exerciseCategory.toDomain(),
                                type = exercise.type.toDomain(),
                                position = exercise.position,
                                plannedSets = exercise.plannedSets.sortedBy { it.position }
                                    .map { set ->
                                        SnapshotPlannedSet(
                                            syncId = set.id,
                                            position = set.position,
                                            weightKg = set.weightKg,
                                            reps = set.reps,
                                            rpeMin = set.rpeMin,
                                            rpeMax = set.rpeMax,
                                        )
                                    },
                            )
                        },
                    )
                },
            )
        },
    ),
)

private fun WorkoutResponseDto.toSnapshot() = SnapshotWorkout(
    syncId = id,
    templateSyncId = workoutTemplateId,
    cycleSyncId = trainingCycleId,
    weekNumber = weekNumber,
    dayNumber = dayNumber,
    title = title,
    isDeload = isDeload,
    status = status.toDomain(),
    date = performedOn,
    startedAt = startedAtUtc,
    finishedAt = finishedAtUtc,
    // Frozen at completion server-side; a session still running has no tonnage to freeze, and
    // recording one would make it look finished.
    totalVolumeKg = if (status == WorkoutSessionStatusDto.IN_PROGRESS) null else totalVolumeKg,
    notes = notes,
    exercises = exercises.sortedBy { it.position }.map(ExerciseLogResponseDto::toSnapshot),
)

private fun ExerciseLogResponseDto.toSnapshot() = SnapshotExerciseLog(
    syncId = id,
    exerciseSyncId = exerciseId,
    exerciseName = exerciseName,
    exerciseCategory = exerciseCategory.toDomain(),
    type = type.toDomain(),
    position = position,
    sets = sets.sortedBy { it.position }.map(SetLogResponseDto::toSnapshot),
)

private fun SetLogResponseDto.toSnapshot() = SnapshotSetLog(
    syncId = id,
    position = position,
    plannedWeightKg = plannedWeightKg,
    plannedReps = plannedReps,
    plannedRpeMin = plannedRpeMin,
    plannedRpeMax = plannedRpeMax,
    actualWeightKg = actualWeightKg,
    actualReps = actualReps,
    actualRpe = actualRpe,
    completed = completed,
    notes = notes,
)

// --- snapshot -> upload requests --------------------------------------------------------------

internal fun SnapshotExercise.toRequest() = ExerciseRequestDto(
    id = syncId,
    name = name,
    category = category.toDto(),
)

internal fun SnapshotCycle.toCreateRequest(exercises: List<SnapshotExercise>) =
    CreateCycleRequestDto(
        // The phone's own identifier, sent as given. The server files the cycle under it, which
        // is what makes a retry after a timeout return the same cycle rather than a second one.
        id = syncId,
        cycleNumber = cycleNumber,
        squatReferenceMaxKg = squatKg,
        benchPressReferenceMaxKg = benchPressKg,
        deadliftReferenceMaxKg = deadliftKg,
        startedAtUtc = startedAt,
        // The whole catalogue, not just the movements this plan happens to prescribe.
        //
        // The server only *needs* the referenced ones, but a movement that exists on the phone
        // and is not uploaded comes back after a restore with a different identity — recreated
        // by the seeder rather than restored — and from then on the two devices disagree about
        // what "Rozpiętki" is. A dozen rows is a cheap price for identities that hold.
        exercises = exercises.map(SnapshotExercise::toRequest),
        program = ProgramRequestDto(
            id = program.syncId.takeIf { it.isNotBlank() },
            name = program.name,
            currentWorkoutTemplateId = program.currentWorkoutTemplateSyncId,
            weeks = program.weeks.map { week ->
                WeekRequestDto(
                    id = week.syncId,
                    weekNumber = week.weekNumber,
                    label = week.label,
                    type = if (week.isDeload) {
                        TrainingWeekTypeDto.DELOAD
                    } else {
                        TrainingWeekTypeDto.TRAINING
                    },
                    workouts = week.workouts.map { template ->
                        WorkoutTemplateRequestDto(
                            id = template.syncId,
                            dayNumber = template.dayNumber,
                            sequenceNumber = template.sequenceNumber,
                            title = template.title,
                            exercises = template.exercises.map { exercise ->
                                ExerciseTemplateRequestDto(
                                    id = exercise.syncId,
                                    position = exercise.position,
                                    exerciseId = exercise.exerciseSyncId,
                                    exerciseName = exercise.exerciseName,
                                    exerciseCategory = exercise.exerciseCategory.toDto(),
                                    type = exercise.type.toDto(),
                                    plannedSets = exercise.plannedSets.map { set ->
                                        PlannedSetRequestDto(
                                            id = set.syncId,
                                            position = set.position,
                                            weightKg = set.weightKg,
                                            reps = set.reps,
                                            rpeMin = set.rpeMin,
                                            rpeMax = set.rpeMax,
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        ),
    )

/**
 * A workout uploaded whole, with its own contents.
 *
 * Always the "bring your own exercises" form rather than "snapshot this template", because a
 * session that was logged offline is the truth about what happened — re-deriving it from a
 * template server-side would replace the lifter's actual sets with the plan's.
 */
internal fun SnapshotWorkout.toCreateRequest() = CreateWorkoutRequestDto(
    id = syncId,
    trainingCycleId = cycleSyncId,
    trainingWeekId = null,
    workoutTemplateId = templateSyncId,
    weekNumber = weekNumber,
    dayNumber = dayNumber,
    title = title,
    isDeload = isDeload,
    status = status.toDto(),
    performedOn = date,
    startedAtUtc = startedAt,
    finishedAtUtc = finishedAt,
    notes = notes,
    exercises = exercises.map { log ->
        ExerciseLogRequestDto(
            id = log.syncId,
            position = log.position,
            exerciseId = log.exerciseSyncId,
            exerciseName = log.exerciseName,
            exerciseCategory = log.exerciseCategory.toDto(),
            type = log.type.toDto(),
            notes = null,
            sets = log.sets.map { set ->
                SetLogRequestDto(
                    id = set.syncId,
                    position = set.position,
                    // Planned and actual sent as two separate facts, exactly as recorded.
                    plannedWeightKg = set.plannedWeightKg,
                    plannedReps = set.plannedReps,
                    plannedRpeMin = set.plannedRpeMin,
                    plannedRpeMax = set.plannedRpeMax,
                    actualWeightKg = set.actualWeightKg,
                    actualReps = set.actualReps,
                    actualRpe = set.actualRpe,
                    completed = set.completed,
                    notes = set.notes,
                )
            },
        )
    },
)

/** Only ever called for a lift that has one; accessory work is filtered out upstream. */
internal fun SnapshotReferenceMax.requiredLift(): LiftTypeDto =
    checkNotNull(category.toLiftDtoOrNull()) {
        "A reference max for $category cannot exist: only the big three have one."
    }

internal fun localDateOrToday(value: LocalDate?): LocalDate = value ?: LocalDate.now(ZoneOffset.UTC)
