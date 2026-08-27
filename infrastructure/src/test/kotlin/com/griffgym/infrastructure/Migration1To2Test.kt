package com.griffgym.infrastructure

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.griffgym.domain.model.CycleStatus
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.Weight
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.migration.GriffGymMigrations
import com.griffgym.infrastructure.database.migration.MIGRATION_1_2
import com.griffgym.infrastructure.repository.RoomReferenceMaxRepository
import com.griffgym.infrastructure.repository.RoomTrainingProgramRepository
import com.griffgym.infrastructure.repository.RoomWorkoutSessionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * The 1 -> 2 upgrade, run against real SQLite.
 *
 * Version 1 knew a single training program and nothing about repeating the block. What this
 * test is really protecting is the promise that upgrading the app never costs a lifter their
 * training history: every row that was on disk before is still there afterwards, and the
 * cycle the old schema could not express is synthesised from what was actually stored.
 */
@RunWith(RobolectricTestRunner::class)
class Migration1To2Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GriffGymDatabase::class.java,
    )

    @Test
    fun `an existing installation gains cycle one, built from the maxes on disk`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.seedVersionOne()
            db.insertCompletedSession()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        migrated.query("SELECT * FROM training_cycle").use { cursor ->
            assertTrue("no cycle was synthesised", cursor.moveToFirst())
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("cycleNumber")))
            // Nothing in version 1 could express a finished program, so the only reading the
            // old schema supports is "the lifter is in it".
            assertEquals(
                CycleStatus.ACTIVE.name,
                cursor.getString(cursor.getColumnIndexOrThrow("status")),
            )
            assertNull(cursor.getStringOrNull("completedAt"))
            // The snapshot is the reference_max table, which is where the block's own numbers
            // have always lived — not a guess.
            assertEquals(210.0, cursor.getDouble(cursor.getColumnIndexOrThrow("squatKg")), 1e-9)
            assertEquals(170.0, cursor.getDouble(cursor.getColumnIndexOrThrow("benchPressKg")), 1e-9)
            assertEquals(225.0, cursor.getDouble(cursor.getColumnIndexOrThrow("deadliftKg")), 1e-9)
            assertEquals(PROGRAM_CREATED_AT, cursor.getLong(cursor.getColumnIndexOrThrow("startedAt")))
            assertEquals(PROGRAM_CREATED_AT, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
            assertFalse("more than one cycle was synthesised", cursor.moveToNext())
        }
    }

    @Test
    fun `the existing program keeps its identity and is pointed at the new cycle`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.seedVersionOne()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        val cycleId = migrated.single("SELECT id FROM training_cycle") { it.getLong(0) }
        migrated.query("SELECT * FROM training_program").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            assertEquals(cycleId, cursor.getLong(cursor.getColumnIndexOrThrow("cycleId")))
            assertEquals("Blok IV — Siła", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals(
                PROGRAM_CREATED_AT,
                cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
            )
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isActive")))
            assertFalse(cursor.moveToNext())
        }
    }

    @Test
    fun `every row that was on disk before the upgrade is still there after it`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.seedVersionOne()
            db.insertCompletedSession()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        assertEquals(2, migrated.count("training_week"))
        assertEquals(6, migrated.count("workout_template"))
        assertEquals(6, migrated.count("exercise_template"))
        assertEquals(11, migrated.count("planned_set"))
        assertEquals(3, migrated.count("reference_max"))
        assertEquals(3, migrated.count("exercise"))
        assertEquals(1, migrated.count("workout_session"))
        assertEquals(1, migrated.count("exercise_log"))
        assertEquals(2, migrated.count("set_log"))

        // The plan below the program is untouched: same ids, same parents, same loads.
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6),
            migrated.list("SELECT sequenceNumber FROM workout_template ORDER BY sequenceNumber") {
                it.getInt(0)
            },
        )
        assertEquals(
            187.5,
            migrated.single("SELECT weightKg FROM planned_set WHERE id = 1") { it.getDouble(0) },
            1e-9,
        )

        // And so is the log: a completed session, its exercise and both of its sets.
        assertEquals(
            "COMPLETED",
            migrated.single("SELECT status FROM workout_session WHERE id = 1") { it.getString(0) },
        )
        assertEquals(
            192.5,
            migrated.single("SELECT actualWeightKg FROM set_log WHERE id = 1") { it.getDouble(0) },
            1e-9,
        )
    }

    @Test
    fun `the progress pointer survives, so the lifter resumes where they stopped`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.seedVersionOne(currentWorkoutTemplateId = 4)
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        assertEquals(
            4L,
            migrated.single("SELECT currentWorkoutTemplateId FROM program_progress") { it.getLong(0) },
        )
        assertEquals(
            1L,
            migrated.single("SELECT programId FROM program_progress") { it.getLong(0) },
        )
    }

    @Test
    fun `an unfinished session is carried across mid-workout`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.seedVersionOne(currentWorkoutTemplateId = 2)
            db.insertInProgressSession()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        assertEquals(
            "IN_PROGRESS",
            migrated.single("SELECT status FROM workout_session WHERE id = 1") { it.getString(0) },
        )
        assertNull(
            migrated.single("SELECT finishedAt FROM workout_session WHERE id = 1") {
                it.getStringOrNull(0)
            },
        )
        assertEquals(2L, migrated.single("SELECT templateId FROM workout_session") { it.getLong(0) })
        // The half-logged set is exactly as it was left.
        assertEquals(1, migrated.count("set_log"))
        assertEquals(
            0,
            migrated.single("SELECT completed FROM set_log WHERE id = 1") { it.getInt(0) },
        )
    }

    @Test
    fun `a database with no program migrates without inventing a cycle`() {
        // What a device looks like between the schema being created and first-run setup
        // finishing: tables, catalogue, nothing else.
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.insertExercises()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        assertEquals(0, migrated.count("training_cycle"))
        assertEquals(0, migrated.count("training_program"))
        assertEquals(3, migrated.count("exercise"))
    }

    @Test
    fun `a program whose maxes went missing still migrates, honestly`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.seedVersionOne(withReferenceMaxes = false)
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // Zero reads as "unknown"; the block generator refuses to build from it, so no plan
        // can ever be calculated off a number nobody entered.
        migrated.query("SELECT * FROM training_cycle").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0.0, cursor.getDouble(cursor.getColumnIndexOrThrow("squatKg")), 1e-9)
        }
        assertEquals(1, migrated.count("training_program"))
    }

    @Test
    fun `the app opens the upgraded database and reads the lifter's history back`() = runTest {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.seedVersionOne(currentWorkoutTemplateId = 2)
            db.insertCompletedSession()
        }

        val database = Room
            .databaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                GriffGymDatabase::class.java,
                TEST_DB,
            )
            .addMigrations(*GriffGymMigrations)
            .allowMainThreadQueries()
            .build()

        try {
            val cycles = cycleRepository(database)
            val cycle = cycles.getCurrentCycle()!!
            assertEquals(1, cycle.cycleNumber)
            assertEquals(CycleStatus.ACTIVE, cycle.status)
            assertEquals(Weight.of(210.0), cycle.referenceMaxes.squat)
            assertEquals(Instant.ofEpochMilli(PROGRAM_CREATED_AT), cycle.startedAt)

            // The plan, its progress and the log all come back through the normal repositories.
            val programs = RoomTrainingProgramRepository(database.trainingProgramDao())
            assertTrue(programs.hasProgram())
            assertEquals(2L, programs.getCurrentWorkoutTemplate()!!.id)
            assertNotNull(cycles.getCycleProgram(cycle.id))

            val summary = cycles.getCycleSummary(cycle.id)!!
            assertEquals(2, summary.weekCount)
            assertEquals(6, summary.plannedWorkouts)
            assertEquals(1, summary.completedWorkouts)

            assertTrue(
                RoomWorkoutSessionRepository(database, database.workoutSessionDao()).hasAnySession(),
            )
            assertEquals(
                Weight.of(170.0),
                RoomReferenceMaxRepository(database, database.referenceMaxDao())
                    .getReferenceMax(ExerciseCategory.BENCH_PRESS)!!
                    .weight,
            )
        } finally {
            database.close()
        }
    }

    // -- version 1 fixtures ---------------------------------------------------------------

    /** A device mid-block on the old schema: catalogue, plan, progress and maxes. */
    private fun SupportSQLiteDatabase.seedVersionOne(
        currentWorkoutTemplateId: Long? = 1,
        withReferenceMaxes: Boolean = true,
    ) {
        insertExercises()

        insert(
            "training_program",
            "id" to 1L,
            "name" to "Blok IV — Siła",
            "createdAt" to PROGRAM_CREATED_AT,
            "isActive" to 1,
        )

        listOf(1 to "ACCUMULATION", 6 to "DELOAD").forEachIndexed { index, (number, label) ->
            insert(
                "training_week",
                "id" to (index + 1).toLong(),
                "programId" to 1L,
                "weekNumber" to number,
                "label" to label,
                "isDeload" to if (number == 6) 1 else 0,
            )
        }

        var templateId = 0L
        (1L..2L).forEach { weekId ->
            (1..3).forEach { day ->
                templateId += 1
                insert(
                    "workout_template",
                    "id" to templateId,
                    "weekId" to weekId,
                    "dayNumber" to day,
                    "sequenceNumber" to templateId.toInt(),
                    "title" to "Day $day",
                )
                insert(
                    "exercise_template",
                    "id" to templateId,
                    "workoutTemplateId" to templateId,
                    "exerciseId" to 1L,
                    "type" to "TOP",
                    "position" to 1,
                )
                (1..if (templateId == 1L) 1 else 2).forEach { position ->
                    insert(
                        "planned_set",
                        "exerciseTemplateId" to templateId,
                        "position" to position,
                        "weightKg" to 187.5,
                        "reps" to 3,
                        "rpeMin" to 8.0,
                        "rpeMax" to 8.0,
                    )
                }
            }
        }

        insert(
            "program_progress",
            "programId" to 1L,
            "currentWorkoutTemplateId" to currentWorkoutTemplateId,
        )

        if (withReferenceMaxes) {
            insert("reference_max", "category" to "SQUAT", "weightKg" to 210.0, "updatedOn" to 20_500L)
            insert(
                "reference_max",
                "category" to "BENCH_PRESS",
                "weightKg" to 170.0,
                "updatedOn" to 20_500L,
            )
            insert(
                "reference_max",
                "category" to "DEADLIFT",
                "weightKg" to 225.0,
                "updatedOn" to 20_500L,
            )
        }
    }

    private fun SupportSQLiteDatabase.insertExercises() {
        listOf(
            Triple(1L, "Przysiad", "SQUAT"),
            Triple(2L, "Ławka", "BENCH_PRESS"),
            Triple(3L, "Martwy ciąg", "DEADLIFT"),
        ).forEach { (id, name, category) ->
            insert("exercise", "id" to id, "name" to name, "category" to category)
        }
    }

    private fun SupportSQLiteDatabase.insertCompletedSession() {
        insert(
            "workout_session",
            "id" to 1L,
            "templateId" to 1L,
            "weekNumber" to 1,
            "dayNumber" to 1,
            "title" to "Day 1",
            "isDeload" to 0,
            "status" to "COMPLETED",
            "date" to 20_500L,
            "startedAt" to SESSION_STARTED_AT,
            "finishedAt" to SESSION_FINISHED_AT,
            "totalVolumeKg" to 1155.0,
            "notes" to "solid",
        )
        insert(
            "exercise_log",
            "id" to 1L,
            "sessionId" to 1L,
            "exerciseId" to 1L,
            "type" to "TOP",
            "position" to 1,
        )
        (1..2).forEach { position ->
            insert(
                "set_log",
                "id" to position.toLong(),
                "exerciseLogId" to 1L,
                "position" to position,
                "plannedWeightKg" to 187.5,
                "plannedReps" to 3,
                "actualWeightKg" to 192.5,
                "actualReps" to 3,
                "actualRpe" to 8.5,
                "completed" to 1,
            )
        }
    }

    private fun SupportSQLiteDatabase.insertInProgressSession() {
        insert(
            "workout_session",
            "id" to 1L,
            "templateId" to 2L,
            "weekNumber" to 1,
            "dayNumber" to 2,
            "title" to "Day 2",
            "isDeload" to 0,
            "status" to "IN_PROGRESS",
            "date" to 20_501L,
            "startedAt" to SESSION_STARTED_AT,
        )
        insert(
            "exercise_log",
            "id" to 1L,
            "sessionId" to 1L,
            "exerciseId" to 1L,
            "type" to "TOP",
            "position" to 1,
        )
        insert(
            "set_log",
            "id" to 1L,
            "exerciseLogId" to 1L,
            "position" to 1,
            "plannedWeightKg" to 187.5,
            "plannedReps" to 3,
            "actualWeightKg" to 187.5,
            "actualReps" to 3,
            "completed" to 0,
        )
    }

    private companion object {
        const val TEST_DB = "migration-test.db"

        /** Epoch millis, the way the Instant converter stores them. */
        const val PROGRAM_CREATED_AT = 1_772_000_000_000L
        const val SESSION_STARTED_AT = 1_772_100_000_000L
        const val SESSION_FINISHED_AT = 1_772_103_600_000L
    }
}

private fun SupportSQLiteDatabase.insert(table: String, vararg values: Pair<String, Any?>) {
    val content = ContentValues()
    values.forEach { (column, value) ->
        when (value) {
            null -> content.putNull(column)
            is Long -> content.put(column, value)
            is Int -> content.put(column, value)
            is Double -> content.put(column, value)
            is String -> content.put(column, value)
            else -> error("Unsupported column type for $column: $value")
        }
    }
    insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, content)
}

private fun SupportSQLiteDatabase.count(table: String): Int =
    single("SELECT COUNT(*) FROM $table") { it.getInt(0) }

private fun <T> SupportSQLiteDatabase.single(sql: String, read: (android.database.Cursor) -> T): T =
    query(sql).use { cursor ->
        check(cursor.moveToFirst()) { "'$sql' returned no rows" }
        read(cursor)
    }

private fun <T> SupportSQLiteDatabase.list(
    sql: String,
    read: (android.database.Cursor) -> T,
): List<T> = query(sql).use { cursor ->
    buildList {
        while (cursor.moveToNext()) add(read(cursor))
    }
}

private fun android.database.Cursor.getStringOrNull(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
}

private fun android.database.Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)
