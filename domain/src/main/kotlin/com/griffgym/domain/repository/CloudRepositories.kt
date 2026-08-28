package com.griffgym.domain.repository

import com.griffgym.domain.model.AuthSession
import com.griffgym.domain.model.BackupProgress
import com.griffgym.domain.model.CloudSyncStatus
import com.griffgym.domain.model.UserMode
import kotlinx.coroutines.flow.Flow

/*
 * The contracts the cloud features are written against. Infrastructure implements them with
 * Retrofit, the Android Keystore and Room; nothing above this line knows that.
 */

/**
 * Which of the two ways of using Griff Gym this installation is on, and the only path
 * between them.
 *
 * The choice outlives the process — a lifter who chose to stay local must not be asked
 * again on the next launch — so it is stored, not held in a ViewModel.
 */
interface UserModeRepository {

    fun observeUserMode(): Flow<UserMode>

    suspend fun getUserMode(): UserMode

    suspend fun chooseLocalOnly()

    /**
     * Only called once a session genuinely exists. For a lifter migrating local data, this
     * comes *after* the backup has been verified, so a failed upload can never leave the app
     * claiming to be backed up.
     */
    suspend fun markAuthenticated(session: AuthSession)

    /** Back to [UserMode.Undecided] on sign-out, so the entry screen is shown again. */
    suspend fun clearAccount()
}

/**
 * Registration, sign-in and sign-out.
 *
 * Passwords are parameters and nothing more: they are never stored, never cached, and never
 * held in any state that outlives the call.
 */
interface AuthRepository {

    /** The session restored from secure storage at startup, if there is one. */
    fun observeSession(): Flow<AuthSession?>

    suspend fun register(email: String, password: String): Result<AuthSession>

    suspend fun login(email: String, password: String): Result<AuthSession>

    /**
     * Signs in with a Google ID token, registering the account on first use.
     *
     * One call for both, because Google has already answered the question the two forms
     * exist to ask: the token names a verified email address, so "do you have an account
     * yet" is the server's problem rather than something to make a lifter choose between.
     *
     * [idToken] is a signed JWT obtained on the device and verified by the server against
     * Google's keys — never an access token, which proves nothing about who is holding it.
     * The token is a parameter and nothing more: like a password it is never stored, and the
     * only thing that outlives this call is the session it mints.
     */
    suspend fun loginWithGoogle(idToken: String): Result<AuthSession>

    /**
     * Revokes the refresh token server-side and clears it from the device.
     *
     * Succeeds even when the server cannot be reached: a lifter who wants to sign out on a
     * train must not be prevented from doing so, and the local tokens are what matter for
     * this device's privacy.
     */
    suspend fun logout(): Result<Unit>

    /**
     * Erases the account itself, server-side, and only then the credentials on this device.
     *
     * The mirror image of [logout], and deliberately so. Signing out is a local act that is
     * allowed to succeed without a network; deleting an account is a *server* act, and the
     * only proof it happened is the server saying so. So the order is fixed: call the API,
     * and clear the stored tokens **only** once it has confirmed.
     *
     * Clearing them first — or on failure — would hand the lifter an app that looks deleted
     * while the account, and every byte of the backup under it, is still there, with no
     * session left to try again with. A failure here therefore leaves this device exactly as
     * it was: still signed in, still able to retry.
     */
    suspend fun deleteAccount(): Result<Unit>

    /** Reads the stored session at startup. Null when there is nothing to restore. */
    suspend fun restoreSession(): AuthSession?

    /** True once the refresh token is gone or has been rejected for good. */
    fun observeSessionExpired(): Flow<Boolean>

    suspend fun acknowledgeSessionExpired()
}

/** What the server holds for this account, before deciding what to do about it. */
enum class CloudStateSummary {
    /** A fresh account with no training data. */
    EMPTY,

    /** There is a backup up there. */
    POPULATED,
}

/**
 * Moving a lifter's whole training history between Room and the server.
 *
 * Both directions are all-or-nothing. A half-restored database — cycles without their
 * programs, sessions without their sets — is worse than no restore at all, because the app
 * would look like it worked.
 */
interface CloudBackupRepository {

    suspend fun readCloudSummary(): Result<CloudStateSummary>

    /**
     * Uploads everything Room holds. Reports progress so the screen can say what it is doing
     * rather than spinning.
     */
    suspend fun backupLocalState(onProgress: suspend (BackupProgress) -> Unit): Result<Unit>

    /**
     * Replaces the local database with the server's copy, inside one Room transaction. On
     * failure the database is left exactly as it was.
     */
    suspend fun restoreCloudState(): Result<Unit>

    /** Sends whatever is waiting. Returns how many records were accepted. */
    suspend fun pushPendingChanges(): Result<Int>

    /** How many local records have not reached the server yet. */
    suspend fun countPendingChanges(): Int

    /**
     * Forgets this account's cached training data on sign-out.
     *
     * The cloud copy is untouched — signing out is not deleting an account. This is about
     * the next person to pick up the phone not finding somebody else's training history.
     */
    suspend fun clearLocalAccountData()
}

/** The status a lifter sees, and the way to ask for a sync by hand. */
interface CloudSyncStatusRepository {

    fun observeStatus(): Flow<CloudSyncStatus>

    /** Queues a background sync. Returns immediately; the work is not done inline. */
    suspend fun requestSync()

    /** Runs a sync now and waits for it. Used by the SYNC NOW button. */
    suspend fun syncNow(): Result<Unit>

    /**
     * Drops every scheduled sync, one-off and periodic.
     *
     * Needed by account deletion, which is about to empty the local database: a background
     * pass waking up halfway through the wipe would read a half-cleared state and try to
     * reconcile it against an account that no longer exists.
     */
    suspend fun cancelScheduledSync()
}

/**
 * Whether the device currently believes it has a connection.
 *
 * Advisory only. "Connected" does not mean the API is reachable — a captive portal, a dead
 * server and a DNS failure all report a healthy network — so every request still has to
 * handle failure properly. This exists to explain to the lifter why nothing is syncing, not
 * to decide whether to try.
 */
interface NetworkMonitor {
    fun observeIsOnline(): Flow<Boolean>
    suspend fun isOnline(): Boolean
}

/**
 * Whether this device holds any training data at all.
 *
 * Its own contract because the answer decides which of four very different things happens
 * after sign-in, and getting it from three separate repositories at each call site invites
 * one of them being forgotten.
 */
interface LocalTrainingDataRepository {
    suspend fun hasAnyTrainingData(): Boolean
}
