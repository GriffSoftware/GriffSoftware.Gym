package com.griffgym.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.griffgym.infrastructure.database.entity.TrainingCycleEntity
import com.griffgym.infrastructure.database.relation.CycleWeekProgressRow
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Counts one row per week of a cycle, with how many of its days were planned and how many
 * have a completed session against them.
 *
 * Done as a single grouped query rather than a query per week: eighteen round trips to
 * render one screen is exactly the N+1 pattern worth avoiding. `COUNT(DISTINCT ...)` keeps
 * the count honest if a lifter ever logs the same template twice.
 */
private const val WEEK_PROGRESS_SELECT =
    "SELECT p.cycleId AS cycleId, w.weekNumber AS weekNumber, w.label AS label, " +
        "w.isDeload AS isDeload, " +
        "COUNT(DISTINCT wt.id) AS plannedWorkouts, " +
        "COUNT(DISTINCT CASE WHEN s.id IS NOT NULL THEN wt.id END) AS completedWorkouts " +
        "FROM training_week w " +
        "JOIN training_program p ON p.id = w.programId " +
        "LEFT JOIN workout_template wt ON wt.weekId = w.id " +
        "LEFT JOIN workout_session s ON s.templateId = wt.id AND s.status = 'COMPLETED' "

private const val WEEK_PROGRESS_GROUP = "GROUP BY w.id ORDER BY p.cycleId, w.weekNumber"

@Dao
interface TrainingCycleDao {

    @Query("SELECT * FROM training_cycle ORDER BY cycleNumber DESC LIMIT 1")
    fun observeCurrent(): Flow<TrainingCycleEntity?>

    @Query("SELECT * FROM training_cycle ORDER BY cycleNumber DESC LIMIT 1")
    suspend fun getCurrent(): TrainingCycleEntity?

    @Query("SELECT * FROM training_cycle ORDER BY cycleNumber DESC")
    fun observeAll(): Flow<List<TrainingCycleEntity>>

    @Query("SELECT * FROM training_cycle WHERE id = :id")
    suspend fun getById(id: Long): TrainingCycleEntity?

    @Insert
    suspend fun insert(cycle: TrainingCycleEntity): Long

    /**
     * The status is a literal because there is exactly one direction a cycle moves in, and a
     * completion time without the matching status would be a contradiction on disk.
     */
    @Query("UPDATE training_cycle SET status = 'COMPLETED', completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Instant)

    /**
     * The week progress of every cycle at once. Grouping in SQL and grouping again in memory
     * costs one query for the whole screen, where a query per cycle would grow with the
     * lifter's training history.
     */
    @Query("$WEEK_PROGRESS_SELECT $WEEK_PROGRESS_GROUP")
    fun observeWeekProgress(): Flow<List<CycleWeekProgressRow>>

    @Query("$WEEK_PROGRESS_SELECT WHERE p.cycleId = :cycleId $WEEK_PROGRESS_GROUP")
    suspend fun getWeekProgress(cycleId: Long): List<CycleWeekProgressRow>
}
