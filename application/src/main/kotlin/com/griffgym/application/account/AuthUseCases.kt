package com.griffgym.application.account

import com.griffgym.domain.model.AuthSession
import com.griffgym.domain.repository.AuthRepository
import com.griffgym.domain.repository.CloudBackupRepository
import com.griffgym.domain.repository.CloudSyncStatusRepository
import com.griffgym.domain.repository.UserModeRepository
import javax.inject.Inject

/**
 * Creates an account.
 *
 * Deliberately does *not* mark the app as authenticated. What happens next depends on what
 * is already on the phone and on the server — see [ResolvePostSignInActionUseCase] — and a
 * lifter with six months of local history must not be recorded as "backed up" before a
 * single byte has been uploaded.
 */
class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): Result<AuthSession> =
        authRepository.register(email.trim(), password)
}

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): Result<AuthSession> =
        authRepository.login(email.trim(), password)
}

/**
 * Ends the session on this device.
 *
 * Three things, in an order chosen so that a failure never leaves the phone showing one
 * lifter's history to another:
 *
 *  1. revoke the refresh token server-side and clear it locally;
 *  2. drop this account's cached training data from Room;
 *  3. return the app to the entry screen.
 *
 * The cloud copy is untouched. Signing out is not deleting an account, and signing back in
 * restores everything.
 */
class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudBackupRepository: CloudBackupRepository,
    private val userModeRepository: UserModeRepository,
) {
    suspend operator fun invoke(): Result<Unit> = runCatching {
        // Even if revocation fails — no signal, server down — the local credentials are gone
        // and the cached data still has to go. Privacy on this device does not depend on
        // reaching the network.
        authRepository.logout()

        cloudBackupRepository.clearLocalAccountData()
        userModeRepository.clearAccount()
    }
}

/**
 * Reads the stored session at startup so a signed-in lifter stays signed in.
 *
 * Never blocks on the network. A phone in a basement gym opens to its own training data
 * exactly as fast as it does with a connection.
 */
class RestoreSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): AuthSession? = authRepository.restoreSession()
}

/**
 * Confirms an account is fully set up and starts the app syncing.
 *
 * Called only once the correct thing has already happened to the data — a completed backup,
 * a completed restore, or the confirmation that there was nothing to move.
 */
class InitializeAuthenticatedSessionUseCase @Inject constructor(
    private val userModeRepository: UserModeRepository,
    private val cloudSyncStatusRepository: CloudSyncStatusRepository,
) {
    suspend operator fun invoke(session: AuthSession) {
        userModeRepository.markAuthenticated(session)
        cloudSyncStatusRepository.requestSync()
    }
}
