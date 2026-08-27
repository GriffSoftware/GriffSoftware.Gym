package com.griffgym.infrastructure

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.migration.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The 2 -> 3 upgrade, run against real SQLite.
 *
 * This is the migration that lets an existing lifter ever have a cloud backup, and it runs on
 * a database that may hold years of training. What it must prove is narrow and absolute:
 * every row that was there before is still there afterwards, unchanged, and each has gained a
 * stable identity the server can file it under.
 */
@RunWith(RobolectricTestRunner::class)
class Migration2To3Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GriffGymDatabase::class.java,
    )

    @Test
    fun `an installation with training history keeps every row and gains sync ids`() {
        helper.createDatabase(TEST_DB, 2).use { db -> db.seedVersionTwo() }

        // runMigrationsAndValidate compares the result against the exported schema 3.json, so
        // a column, index or default that drifts from what a fresh install creates fails here.
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        migrated.assertRowCount("exercise", 2)
        migrated.assertRowCount("training_cycle", 1)
        migrated.assertRowCount("training_program", 1)
        migrated.assertRowCount("training_week", 1)
        migrated.assertRowCount("workout_template", 1)
        migrated.assertRowCount("exercise_template", 1)
        migrated.assertRowCount("planned_set", 2)
        migrated.assertRowCount("workout_session", 1)
        migrated.assertRowCount("exercise_log", 1)
        migrated.assertRowCount("set_log", 2)
        migrated.assertRowCount("reference_max", 3)

        // The training data itself is untouched. A migration that renumbered a cycle or
        // rounded a load would be worse than one that failed outright.
        migrated.query("SELECT cycleNumber, squatKg, status FROM training_cycle").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(210.0, cursor.getDouble(1), 0.001)
            assertEquals("ACTIVE", cursor.getString(2))
        }

        migrated.query(
            "SELECT plannedWeightKg, actualWeightKg, actualReps, completed FROM set_log ORDER BY position",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(187.5, cursor.getDouble(0), 0.001)
            assertEquals(190.0, cursor.getDouble(1), 0.001)
            assertEquals(3, cursor.getInt(2))
            assertEquals(1, cursor.getInt(3))
        }

        migrated.close()
    }

    @Test
    fun `every synced row gets a distinct, well formed uuid`() {
        helper.createDatabase(TEST_DB, 2).use { db -> db.seedVersionTwo() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        SYNCED_TABLES.forEach { table ->
            val ids = migrated.readColumn("SELECT syncId FROM `$table`")

            assertTrue("$table has no rows to check", ids.isNotEmpty())
            assertTrue(
                "$table has a row with no sync id",
                ids.all { it.isNotBlank() },
            )
            assertTrue(
                "$table produced a malformed uuid: $ids",
                ids.all(UUID_PATTERN::matches),
            )
            // Per-row, not per-statement: two set logs must not end up sharing an identity,
            // or the server would treat one as an edit of the other.
            assertEquals("$table repeated a sync id", ids.size, ids.toSet().size)
        }

        // Distinct across tables too, since the whole point is a globally addressable record.
        val everything = SYNCED_TABLES.flatMap { migrated.readColumn("SELECT syncId FROM `$it`") }
        assertEquals("a sync id was reused across tables", everything.size, everything.toSet().size)

        migrated.close()
    }

    @Test
    fun `sync metadata starts empty, because nothing has been near a server`() {
        helper.createDatabase(TEST_DB, 2).use { db -> db.seedVersionTwo() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        migrated.assertRowCount("sync_metadata", 0)
        assertNotNull(migrated.query("SELECT * FROM sync_metadata LIMIT 0"))

        migrated.close()
    }

    @Test
    fun `an empty database migrates cleanly`() {
        // A fresh install that happened to be created on version 2 and never used. The UPDATE
        // statements match no rows, which must be fine rather than an error.
        helper.createDatabase(TEST_DB, 2).use { }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        SYNCED_TABLES.forEach { migrated.assertRowCount(it, 0) }

        migrated.close()
    }

    // -----------------------------------------------------------------------------------------

    private fun SupportSQLiteDatabase.assertRowCount(table: String, expected: Int) {
        query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("unexpected row count in $table", expected, cursor.getInt(0))
        }
    }

    private fun SupportSQLiteDatabase.readColumn(sql: String): List<String> =
        query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    /** A believable version 2 database: one cycle, its plan, and a workout logged against it. */
    private fun SupportSQLiteDatabase.seedVersionTwo() {
        execSQL(
            "INSERT INTO exercise (id, name, category) VALUES " +
                "(1, 'Przysiad', 'SQUAT'), (2, 'Ławka', 'BENCH_PRESS')",
        )
        execSQL(
            "INSERT INTO reference_max (category, weightKg, updatedOn) VALUES " +
                "('SQUAT', 210.0, 20000), ('BENCH_PRESS', 170.0, 20000), ('DEADLIFT', 225.0, 20000)",
        )
        execSQL(
            "INSERT INTO training_cycle " +
                "(id, cycleNumber, status, startedAt, completedAt, squatKg, benchPressKg, deadliftKg, createdAt) " +
                "VALUES (1, 1, 'ACTIVE', 1700000000000, NULL, 210.0, 170.0, 225.0, 1700000000000)",
        )
        execSQL(
            "INSERT INTO training_program (id, cycleId, name, createdAt, isActive) " +
                "VALUES (1, 1, 'Blok IV — Siła', 1700000000000, 1)",
        )
        execSQL(
            "INSERT INTO training_week (id, programId, weekNumber, label, isDeload) " +
                "VALUES (1, 1, 1, 'ACCUMULATION', 0)",
        )
        execSQL(
            "INSERT INTO workout_template (id, weekId, dayNumber, sequenceNumber, title) " +
                "VALUES (1, 1, 1, 1, 'Squat Focus / Bench Volume')",
        )
        execSQL(
            "INSERT INTO exercise_template (id, workoutTemplateId, exerciseId, type, position) " +
                "VALUES (1, 1, 1, 'TOP', 1)",
        )
        execSQL(
            "INSERT INTO planned_set (id, exerciseTemplateId, position, weightKg, reps, rpeMin, rpeMax) " +
                "VALUES (1, 1, 1, 187.5, 3, 8.0, 8.0), (2, 1, 2, 162.5, 3, 6.0, 7.0)",
        )
        execSQL(
            "INSERT INTO workout_session " +
                "(id, templateId, weekNumber, dayNumber, title, isDeload, status, date, " +
                "startedAt, finishedAt, totalVolumeKg, notes) " +
                "VALUES (1, 1, 1, 1, 'Squat Focus / Bench Volume', 0, 'COMPLETED', 20000, " +
                "1700000000000, 1700003600000, 1140.0, NULL)",
        )
        execSQL(
            "INSERT INTO exercise_log (id, sessionId, exerciseId, type, position) " +
                "VALUES (1, 1, 1, 'TOP', 1)",
        )
        execSQL(
            "INSERT INTO set_log " +
                "(id, exerciseLogId, position, plannedWeightKg, plannedReps, plannedRpeMin, " +
                "plannedRpeMax, actualWeightKg, actualReps, actualRpe, completed, notes) " +
                "VALUES (1, 1, 1, 187.5, 3, 8.0, 8.0, 190.0, 3, 8.5, 1, NULL), " +
                "(2, 1, 2, 162.5, 3, 6.0, 7.0, 162.5, 3, 7.0, 1, NULL)",
        )
    }

    private companion object {
        const val TEST_DB = "migration-2-3-test.db"

        val SYNCED_TABLES = listOf(
            "exercise",
            "reference_max",
            "training_cycle",
            "training_program",
            "training_week",
            "workout_template",
            "exercise_template",
            "planned_set",
            "workout_session",
            "exercise_log",
            "set_log",
        )

        /** Canonical 8-4-4-4-12, version nibble 4, variant nibble 8/9/a/b. */
        val UUID_PATTERN =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
