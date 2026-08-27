package com.griffgym.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * What the server knows about one local aggregate.
 *
 * A table of its own rather than columns on the training tables, for two reasons. The
 * training schema describes what a lifter did; whether a row has reached a server is not part
 * of that and should not have to be migrated alongside it. And a local-only lifter — who is
 * the majority case — carries no sync bookkeeping at all: the table is simply empty.
 *
 * Keyed by [entityType] plus [entityId], where the id is the record's stable sync id and not
 * its Room row id. The pairing is what the server would recognise, and it survives the local
 * database being rebuilt.
 */
@Entity(
    tableName = "sync_metadata",
    primaryKeys = ["entityType", "entityId"],
    indices = [Index("syncState")],
)
data class SyncMetadataEntity(
    val entityType: SyncEntityType,
    val entityId: String,
    val syncState: SyncState,
    /** The revision the server last confirmed, sent back on the next write to detect conflicts. */
    val serverVersion: Int?,
    val lastAttemptAtUtc: Long?,
    /** Set only when a write was actually accepted. A started sync is not a backup. */
    val lastSyncedAtUtc: Long?,
    /** Kept for diagnostics. Never shown to the lifter — they get a state, not a stack trace. */
    val failureMessage: String?,
)
