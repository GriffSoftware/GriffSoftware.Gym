package com.griffgym.infrastructure.database.entity

import java.util.UUID

/**
 * The identity a record keeps for its whole life, on this phone and on the server.
 *
 * Room's `Long` primary keys stay exactly as they are — every foreign key, every DAO query
 * and every existing row depends on them, and rewriting all of that would be a migration
 * with far more risk than reward. What each synchronised row gains is a second, *stable*
 * identity: a UUID that the server also knows it by.
 *
 * It is minted here, on the device, at the moment the row is created — before anything has
 * been uploaded and whether or not there is an account. That is what lets a lifter train
 * offline for six months and then hand the whole history to a server that has never seen it:
 * the rows already have the identities the server will file them under, so nothing has to be
 * renumbered at the one moment when a mistake would be least recoverable.
 */
internal fun newSyncId(): String = UUID.randomUUID().toString()

/** Which kind of record a [SyncMetadataEntity] row is about. */
enum class SyncEntityType {
    REFERENCE_MAX,
    EXERCISE,
    TRAINING_CYCLE,
    WORKOUT_SESSION,
}

/**
 * Where one record stands relative to the server.
 *
 * Tracked per *aggregate* — a cycle with its whole plan, a session with all its sets —
 * because that is the unit the API accepts and the unit a client can render. Half a workout
 * is not a state anything downstream could do anything sensible with.
 */
enum class SyncState {

    /** The server has this exact revision. */
    SYNCED,

    /** Changed locally and waiting for a connection. The normal state during a workout. */
    PENDING_UPLOAD,

    /** Removed locally; the server has not been told yet. */
    PENDING_DELETE,

    /**
     * The server refused the write because it had already moved on. The local version is
     * kept untouched — this is the state that exists so that a conflict is never resolved by
     * quietly throwing one side away.
     */
    CONFLICT,

    /** The last attempt failed for a reason worth retrying. */
    FAILED,
}
