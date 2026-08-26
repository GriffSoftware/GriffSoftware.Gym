package com.griffgym.infrastructure.mapper

import com.griffgym.domain.model.ExerciseLog
import com.griffgym.domain.model.Rpe
import com.griffgym.domain.model.SetLog
import com.griffgym.domain.model.Weight
import com.griffgym.domain.model.WorkoutSession
import com.griffgym.infrastructure.database.entity.SetLogEntity
import com.griffgym.infrastructure.database.relation.ExerciseLogWithDetails
import com.griffgym.infrastructure.database.relation.WorkoutSessionWithExercises
import java.time.LocalDate

internal fun SetLogEntity.toDomain(): SetLog = SetLog(
    id = id,
    position = position,
    plannedWeight = plannedWeightKg?.let(Weight::of),
    plannedReps = plannedReps,
    plannedRpe = toRpeTarget(plannedRpeMin, plannedRpeMax),
    actualWeight = actualWeightKg?.let(Weight::of),
    actualReps = actualReps,
    actualRpe = actualRpe?.let(Rpe::ofOrNull),
    completed = completed,
    notes = notes,
)

internal fun ExerciseLogWithDetails.toDomain(): ExerciseLog = ExerciseLog(
    id = log.id,
    position = log.position,
    exercise = exercise.toDomain(),
    type = log.type,
    sets = sets.sortedBy { it.position }.map { it.toDomain() },
)

internal fun WorkoutSessionWithExercises.toDomain(): WorkoutSession = WorkoutSession(
    id = session.id,
    templateId = session.templateId,
    weekNumber = session.weekNumber,
    dayNumber = session.dayNumber,
    title = session.title,
    isDeload = session.isDeload,
    status = session.status,
    date = LocalDate.ofEpochDay(session.date),
    startedAt = session.startedAt,
    finishedAt = session.finishedAt,
    notes = session.notes,
    exercises = exercises.sortedBy { it.log.position }.map { it.toDomain() },
)
