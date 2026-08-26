package com.griffgym.domain.repository

import com.griffgym.domain.model.Exercise
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.GeneratedProgram
import com.griffgym.domain.model.ReferenceMax
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.SetResult
import com.griffgym.domain.model.TrainingCycle
import com.griffgym.domain.model.TrainingCycleSummary
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

    /** Cheap existence check for startup, without materialising the whole plan. */
    suspend fun hasProgram(): Boolean

    /** The next unit of the program that has not been completed yet. */
    fun observeCurrentWorkoutTemplate(): Flow<WorkoutTemplate?>
    suspend fun getCurrentWorkoutTemplate(): WorkoutTemplate?

    fun observeCurrentTrainingWeek(): Flow<TrainingWeek?>

    suspend fun getWorkoutTemplate(id: Long): WorkoutTemplate?

    /** The unit that follows [sequenceNumber] in program order, or `null` at the end. */
    suspend fun getWorkoutTemplateAfter(sequenceNumber: Int): WorkoutTemplate?

    suspend fun setCurrentWorkoutTemplate(templateId: Long?)
}

/**
 * The lifter's training cycles: the one they are in, the ones behind them, and the single
 * path by which a new one comes into existence.
 *
 * A cycle owns its program rather than the other way round, so creating one is one unit of
 * work — new cycle, new plan, new live reference maxes, previous plan stood down — and
 * closing one is another. Both are transactional in the Room implementation.
 */
interface TrainingCycleRepository {

    /**
     * The cycle the lifter is in or has just finished: the highest numbered one.
     *
     * Its [com.griffgym.domain.model.CycleStatus] answers which of the two it is, so callers
     * never have to reconstruct "am I training or am I deciding?" from two separate reads.
     */
    fun observeCurrentCycle(): Flow<TrainingCycle?>
    suspend fun getCurrentCycle(): TrainingCycle?

    suspend fun getCycle(id: Long): TrainingCycle?

    /**
     * Every cycle with its week-by-week progress, newest first: the current one followed by
     * the history behind it.
     *
     * One flow rather than "the current cycle" plus "the rest" because the cycles screen
     * shows both at once and they must never disagree about which cycle is which. Progress
     * is counted from completed sessions on every read, so it cannot drift away from the log.
     */
    fun observeCycleSummaries(): Flow<List<TrainingCycleSummary>>

    suspend fun getCycleSummary(cycleId: Long): TrainingCycleSummary?

    /** The persisted plan of [cycleId], for the read-only detail screen. */
    suspend fun getCycleProgram(cycleId: Long): TrainingProgram?

    /**
     * Starts a cycle: the row itself, the [program] generated for it, the progress pointer at
     * its first unit, and [referenceMaxes] written both onto the cycle as its permanent
     * snapshot and into the live reference max table the rest of the app reads.
     *
     * If a previous cycle is still open it is closed and its program stood down here too, so
     * there is never a moment with two active programs. Implementations must write all of it
     * in a single transaction: a half-started cycle is a lifter with no plan they can train.
     */
    suspend fun startCycle(
        program: GeneratedProgram,
        referenceMaxes: ReferenceMaxSnapshot,
        date: LocalDate,
        startedAt: Instant,
    ): TrainingCycle

    /**
     * Closes the current cycle, clearing its program's progress pointer in the same
     * transaction — "there is no next workout" and "the cycle is finished" are one fact.
     *
     * Returns the closed cycle, or null if there was nothing open to close.
     */
    suspend fun completeCurrentCycle(completedAt: Instant): TrainingCycle?
}

interface WorkoutSessionRepository {
    fun observeActiveSession(): Flow<WorkoutSession?>
    suspend fun getActiveSession(): WorkoutSession?

    /** Whether anything has ever been logged — active, completed or cancelled. */
    suspend fun hasAnySession(): Boolean

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

    /** Whether the lifter already has planning numbers on file. */
    suspend fun hasAnyReferenceMax(): Boolean
}

/**
 * The one flag that says first-run setup is behind us.
 *
 * Deliberately kept outside the training database: it is application state, not training
 * history, and it must survive independently of whatever happens to the plan.
 */
interface OnboardingRepository {
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun markOnboardingCompleted()
}
