package com.griffgym.infrastructure.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Gives every synchronised row a stable identity the server can also use, and adds the table
 * that tracks what has reached the server.
 *
 * Nothing is dropped, renamed or recomputed. The `Long` primary keys stay exactly as they
 * are — every foreign key and every DAO query is built on them — and each table simply gains
 * a `syncId` alongside. An installation with two years of history comes out of this migration
 * with two years of history, now addressable by both identities.
 *
 * `sync_metadata` is created empty on purpose. Nothing here has been near a server, so
 * claiming otherwise would be a lie the app would then act on; a lifter who later creates an
 * account gets the rows written as their first backup actually lands.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {

    /**
     * Every table that maps to something the Griff Gym API stores.
     *
     * `program_progress` is deliberately absent: it is a pointer at the current unit, not a
     * record in its own right, and the server carries it as a field on the training program.
     */
    private val syncedTables = listOf(
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

    override fun migrate(db: SupportSQLiteDatabase) {
        syncedTables.forEach { table -> addSyncId(db, table) }
        createSyncMetadataTable(db)
    }

    /**
     * SQLite can only add a column with a constant default, so the column arrives empty and is
     * filled immediately afterwards. The default is declared on the entity too, so a fresh
     * install and a migrated one end up with byte-identical schemas — which is what the
     * migration test actually checks.
     */
    private fun addSyncId(db: SupportSQLiteDatabase, table: String) {
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")

        // One statement, one UUID per row: randomblob() is re-evaluated for every row, which
        // is exactly what makes this safe to run against a table of any size.
        db.execSQL("UPDATE `$table` SET `syncId` = $UUID_V4_SQL")

        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_syncId` ON `$table` (`syncId`)",
        )
    }

    private fun createSyncMetadataTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_metadata` (" +
                "`entityType` TEXT NOT NULL, " +
                "`entityId` TEXT NOT NULL, " +
                "`syncState` TEXT NOT NULL, " +
                "`serverVersion` INTEGER, " +
                "`lastAttemptAtUtc` INTEGER, " +
                "`lastSyncedAtUtc` INTEGER, " +
                "`failureMessage` TEXT, " +
                "PRIMARY KEY(`entityType`, `entityId`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sync_metadata_syncState` " +
                "ON `sync_metadata` (`syncState`)",
        )
    }
}

/**
 * A version 4 UUID built out of SQLite primitives, in the canonical 8-4-4-4-12 form with the
 * version and variant nibbles set correctly.
 *
 * Generated in SQL rather than by reading every row into Kotlin and writing it back: a lifter
 * several years in has tens of thousands of set logs, and a migration that walks all of them
 * one at a time is a migration that looks like a hang on a cold start.
 */
private const val UUID_V4_SQL =
    "lower(" +
        "hex(randomblob(4)) || '-' || " +
        "hex(randomblob(2)) || '-4' || " +
        "substr(hex(randomblob(2)), 2) || '-' || " +
        "substr('89ab', abs(random()) % 4 + 1, 1) || " +
        "substr(hex(randomblob(2)), 2) || '-' || " +
        "hex(randomblob(6))" +
        ")"
