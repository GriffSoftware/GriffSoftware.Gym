package com.griffgym.infrastructure.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Introduces training cycles.
 *
 * Version 1 knew one training program and nothing about repeating the block. Version 2 makes
 * a program belong to a numbered [com.griffgym.infrastructure.database.entity.TrainingCycleEntity]
 * that also records the reference maxes it was generated from.
 *
 * For an installation that already holds a program, the migration synthesises **cycle 1,
 * ACTIVE**, from the data actually on disk and points the existing program at it. Treating a
 * pre-existing program as the active cycle 1 is the only reading the old schema supports:
 * nothing ever set `isActive = 0`, so version 1 had no way to express a finished program.
 * A lifter who had in fact already finished their block is not stranded by that choice —
 * `GetCurrentWorkoutUseCase` treats "no next workout" as a finished cycle regardless of the
 * stored status, so they land on the review screen rather than a dead end.
 *
 * Nothing is dropped, rewritten or recomputed: sessions, set logs, reference maxes and the
 * whole plan are carried across untouched.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        createCycleTable(db)
        val cycleId = synthesiseFirstCycle(db)
        addCycleIdToPrograms(db, cycleId)
    }

    private fun createCycleTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `training_cycle` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`cycleNumber` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, " +
                "`completedAt` INTEGER, " +
                "`squatKg` REAL NOT NULL, " +
                "`benchPressKg` REAL NOT NULL, " +
                "`deadliftKg` REAL NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_training_cycle_cycleNumber` " +
                "ON `training_cycle` (`cycleNumber`)",
        )
    }

    /**
     * Writes the cycle 1 row an existing installation implicitly always had, or nothing at
     * all on a database with no program yet — the shape a fresh install has between the
     * schema being created and first-run setup finishing.
     *
     * The snapshot is taken from the `reference_max` table, which is where the block's own
     * numbers have always lived. A lift with no row falls back to **0.0 kg**: the app has
     * always required all three maxes before a program could exist, so this cannot arise
     * from normal use, and a zero reads honestly as "unknown" instead of inventing a
     * plausible-looking number that would then be compared against and progressed from.
     * Zero is safe to store — the snapshot is a historical record, and the block generator
     * rejects zero maxes on its own, so no plan can ever be built from one.
     */
    private fun synthesiseFirstCycle(db: SupportSQLiteDatabase): Long? {
        val createdAt = db.query(
            "SELECT createdAt FROM training_program ORDER BY id LIMIT 1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        } ?: return null

        val maxes = mutableMapOf<String, Double>()
        db.query("SELECT category, weightKg FROM reference_max").use { cursor ->
            while (cursor.moveToNext()) {
                maxes[cursor.getString(0)] = cursor.getDouble(1)
            }
        }

        db.execSQL(
            "INSERT INTO `training_cycle` " +
                "(`cycleNumber`, `status`, `startedAt`, `completedAt`, " +
                "`squatKg`, `benchPressKg`, `deadliftKg`, `createdAt`) " +
                "VALUES (1, 'ACTIVE', ?, NULL, ?, ?, ?, ?)",
            arrayOf<Any>(
                createdAt,
                maxes["SQUAT"] ?: 0.0,
                maxes["BENCH_PRESS"] ?: 0.0,
                maxes["DEADLIFT"] ?: 0.0,
                createdAt,
            ),
        )

        return db.query("SELECT id FROM training_cycle WHERE cycleNumber = 1").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    /**
     * `training_program` gains a non-null `cycleId`, which SQLite cannot add in place without
     * a default value — and a default would not match the schema Room expects. So the table
     * is rebuilt: same name, same ids, same rows, one extra column.
     *
     * Foreign keys are off for the duration of a Room migration, so dropping and recreating
     * the parent is safe; `training_week` and `program_progress` reference it by name and by
     * ids that are copied across unchanged, so nothing below it moves.
     */
    private fun addCycleIdToPrograms(db: SupportSQLiteDatabase, cycleId: Long?) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `training_program_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`cycleId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`isActive` INTEGER NOT NULL, " +
                "FOREIGN KEY(`cycleId`) REFERENCES `training_cycle`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )

        if (cycleId != null) {
            // Every program that existed belongs to the one cycle just synthesised. There has
            // only ever been one, but pointing all of them at it keeps the column non-null
            // without discarding a row even in a database that somehow holds more.
            db.execSQL(
                "INSERT INTO `training_program_new` (`id`, `cycleId`, `name`, `createdAt`, `isActive`) " +
                    "SELECT `id`, ?, `name`, `createdAt`, `isActive` FROM `training_program`",
                arrayOf<Any>(cycleId),
            )
        }

        db.execSQL("DROP TABLE `training_program`")
        db.execSQL("ALTER TABLE `training_program_new` RENAME TO `training_program`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_training_program_cycleId` " +
                "ON `training_program` (`cycleId`)",
        )
    }
}

/** Every migration the app ships, in order. Room picks the path it needs from this list. */
val GriffGymMigrations: Array<Migration> = arrayOf(MIGRATION_1_2)
