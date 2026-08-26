package com.griffgym.domain.repository

import com.griffgym.domain.model.Exercise
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.ReferenceMax
import com.griffgym.domain.model.SetResult
import com.griffgym.domain.model.TrainingProgram
import com.griffgym.domain.model.TrainingVolume
import com.griffgym.domain.model.TrainingWeek
import com.griffgym.domain.model.Weight
import com.griffgym.domain.model.WorkoutSession
import com.griffgym.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface ExerciseRepository {
    fun observeExercises(): Flow<List<Exercise>>
    suspend fun getExercise(id: Long): Exercise?
}

interface TrainingProgramRepository {
    fun observeActiveProgram(): Flow<TrainingProgram?>
    suspend fun getActiveProgram(): TrainingProgram?

    /** The next unit of the program that has not been completed yet. */
    fun observeCurrentWorkoutTemplate(): Flow<WorkoutTemplate?>
    suspend fun getCurrentWorkoutTemplate(): WorkoutTemplate?

    fun observeCurrentTrainingWeek(): Flow<TrainingWeek?>

    suspend fun getWorkoutTemplate(id: Long): WorkoutTemplate?

    /** The unit that follows [sequenceNumber] in program order, or `null` at the end. */
    suspend fun getWorkoutTemplateAfter(sequenceNumber: Int): WorkoutTemplate?

    suspend fun setCurrentWorkoutTemplate(templateId: Long?)
}

interface WorkoutSessionRepository {
    fun observeActiveSession(): Flow<WorkoutSession?>
    suspend fun getActiveSession(): WorkoutSession?

    fun observeSession(id: Long): Flow<WorkoutSession?>
    suspend fun getSession(id: Long): WorkoutSession?

    /** Completed and cancelled sessions, newest first. */
    fun observeHistory(): Flow<List<WorkoutSession>>

    /** Completed sessions only, oldest first — the input for every statistic. */
    fun observeCompletedSessions(): Flow<List<WorkoutSession>>

    /** Snapshots [template] into a new in-progress session and returns its id. */
    suspend fun startSession(template: WorkoutTemplate, date: LocalDate, startedAt: Instant): Long

    suspend fun updateSet(setLogId: Long, result: SetResult)

    suspend fun completeSession(sessionId: Long, finishedAt: Instant, totalVolume: TrainingVolume)

    suspend fun cancelSession(sessionId: Long, finishedAt: Instant)

    suspend fun updateSessionNotes(sessionId: Long, notes: String?)

    suspend fun addExercise(sessionId: Long, exerciseId: Long, type: ExerciseType): Long

    suspend fun addSet(exerciseLogId: Long): Long

    suspend fun removeSet(setLogId: Long)
}

interface ReferenceMaxRepository {
    fun observeReferenceMaxes(): Flow<List<ReferenceMax>>
    suspend fun getReferenceMax(category: ExerciseCategory): ReferenceMax?
    suspend fun updateReferenceMax(category: ExerciseCategory, weight: Weight, updatedOn: LocalDate)
}
