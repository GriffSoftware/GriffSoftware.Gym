package com.griffgym.domain.model

import java.time.Instant

/**
 * How this installation stores the lifter's training data.
 *
 * A deliberate, explicit choice rather than something inferred from "is there a token?".
 * The difference matters: a lifter with no account has made a decision the app must respect
 * and must not keep re-asking about, and a lifter whose session expired is still an account
 * holder whose data belongs in the cloud.
 */
sealed interface UserMode {

    /**
     * The lifter has not chosen yet. Only ever true before the data-protection screen has
     * been answered once — including for an installation that predates accounts entirely,
     * which is why that screen must never assume an empty database.
     */
    data object Undecided : UserMode

    /**
     * Everything lives on this phone and nowhere else. Fully functional: every training
     * feature works, because an account is a backup, not a licence.
     */
    data object LocalOnly : UserMode

    /** Signed in. Room is still the source of truth; the cloud is a durable copy of it. */
    data class Authenticated(val userId: String, val email: String) : UserMode

    val isAuthenticated: Boolean get() = this is Authenticated

    val hasChosen: Boolean get() = this != Undecided
}

/**
 * What the app knows about the signed-in account.
 *
 * Deliberately does not carry tokens. Nothing above infrastructure has any use for a JWT,
 * and a value that never leaves that layer cannot be logged, put in a UI state, or written
 * into a saved-state bundle by accident.
 */
data class AuthSession(val userId: String, val email: String)

/** Where a lifter's data stands relative to their cloud backup. */
enum class CloudSyncState {

    /** No account. Room is the only copy, and the app says so plainly. */
    LOCAL_ONLY,

    /** Everything on this device has reached the server. */
    SYNCED,

    /** A backup is running right now. */
    SYNCING,

    /** Local changes are waiting for a connection. */
    PENDING,

    /** Signed in, but unreachable. Training carries on; the backup waits. */
    OFFLINE,

    /** The last attempt failed. Local data is untouched and safe. */
    ERROR,

    /**
     * The server moved on before this device's change landed. Nothing is overwritten and
     * nothing is discarded — the local version is kept until it can be resolved.
     */
    CONFLICT,
}

/**
 * The one-line answer to "is my training safe?", which is the only question a lifter
 * actually has about any of this.
 */
data class CloudSyncStatus(
    val state: CloudSyncState,
    /** Set only when a backup actually finished. A started sync is not a backup. */
    val lastSyncedAt: Instant? = null,
    val pendingChanges: Int = 0,
) {
    val isBackedUp: Boolean get() = state == CloudSyncState.SYNCED

    companion object {
        val LocalOnly: CloudSyncStatus = CloudSyncStatus(CloudSyncState.LOCAL_ONLY)
    }
}

/** How far a first backup has got, for a screen that must not look frozen. */
enum class BackupStage {
    PREPARING,
    UPLOADING_REFERENCE_MAXES,
    UPLOADING_CYCLES,
    UPLOADING_WORKOUTS,
    VERIFYING,
    DONE,
}

data class BackupProgress(
    val stage: BackupStage,
    val completed: Int = 0,
    val total: Int = 0,
) {
    val fraction: Float get() = if (total <= 0) 0f else completed.toFloat() / total
}
