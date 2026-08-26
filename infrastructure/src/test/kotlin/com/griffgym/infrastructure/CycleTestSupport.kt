package com.griffgym.infrastructure

import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.StrengthBlockTemplate
import com.griffgym.domain.model.TrainingBlockGenerator
import com.griffgym.domain.model.TrainingCycle
import com.griffgym.domain.model.Weight
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.dao.ReferenceMaxDao
import com.griffgym.infrastructure.repository.GeneratedProgramWriter
import com.griffgym.infrastructure.repository.RoomTrainingCycleRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Wiring the infrastructure tests share.
 *
 * There is one way a program comes into existence — [RoomTrainingCycleRepository.startCycle] —
 * so the tests build one the same way the app does rather than reaching past it into the DAOs.
 */
internal fun cycleRepository(
    database: GriffGymDatabase,
    referenceMaxDao: ReferenceMaxDao = database.referenceMaxDao(),
): RoomTrainingCycleRepository = RoomTrainingCycleRepository(
    database = database,
    cycleDao = database.trainingCycleDao(),
    programDao = database.trainingProgramDao(),
    referenceMaxDao = referenceMaxDao,
    programWriter = GeneratedProgramWriter(
        programDao = database.trainingProgramDao(),
        exerciseDao = database.exerciseDao(),
    ),
)

/** Generates the block from [maxes] and starts a cycle on it, exactly as the app does. */
internal suspend fun RoomTrainingCycleRepository.startCycleFrom(
    maxes: Map<ExerciseCategory, Weight>,
    clock: Clock,
): TrainingCycle = startCycle(
    program = TrainingBlockGenerator.generate(StrengthBlockTemplate.template, maxes),
    referenceMaxes = ReferenceMaxSnapshot.of(maxes),
    date = LocalDate.now(clock),
    startedAt = clock.instant(),
)

internal suspend fun RoomTrainingCycleRepository.startCycleFrom(
    maxes: Map<ExerciseCategory, Weight>,
    at: Instant,
    date: LocalDate,
): TrainingCycle = startCycle(
    program = TrainingBlockGenerator.generate(StrengthBlockTemplate.template, maxes),
    referenceMaxes = ReferenceMaxSnapshot.of(maxes),
    date = date,
    startedAt = at,
)
