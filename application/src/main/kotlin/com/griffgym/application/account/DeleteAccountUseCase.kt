package com.griffgym.application.account

import com.griffgym.domain.repository.AuthRepository
import com.griffgym.domain.repository.CloudBackupRepository
import com.griffgym.domain.repository.CloudSyncStatusRepository
import com.griffgym.domain.repository.OnboardingRepository
import com.griffgym.domain.repository.UserModeRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Deletes the lifter's account, everything the server holds under it, and every trace of it
 * left on this phone.
 *
 * The one irreversible action in the app. Two rules follow from that, and the order of the
 * calls below is entirely determined by them.
 *
 * **The server goes first.** [AuthRepository.deleteAccount] is the only thing that can say
 * the account is actually gone. Until it has, nothing local is touched: a wipe on an offline
 * phone would destroy the lifter's training while leaving the account and its backup exactly
 * where they were, and no screen afterwards could offer either back. A failure therefore
 * returns the failure and changes nothing — no retry is queued, no "delete when back
 * online" is remembered, because a deletion that happens hours later, unattended, is not
 * something anybody consented to.
 *
 * **The workers go before the database.** [CloudSyncStatusRepository.cancelScheduledSync]
 * precedes [CloudBackupRepository.clearLocalAccountData] so a background pass cannot wake up
 * in the middle of the wipe, read a half-cleared database and start reconciling it against
 * an account that no longer exists. Cancelling after would leave exactly that window open.
 * Cancellation alone is not enough — it is asynchronous and cooperative — so the wipe also
 * holds the sync lock; see `CloudBackupRepositoryImpl.clearLocalAccountData`.
 *
 * **The identity goes before the data.** [UserModeRepository.clearAccount] returns the
 * installation to "undecided" and [OnboardingRepository.clearOnboardingCompleted] means the
 * next launch offers first-run setup rather than an empty Home — and both happen *before* the
 * database is emptied, because these writes are not one transaction and the app has to
 * survive being killed between any two of them. See `forgetAccountLocally` for why that
 * particular order is the one that fails safely.
 */
class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudSyncStatusRepository: CloudSyncStatusRepository,
    private val cloudBackupRepository: CloudBackupRepository,
    private val userModeRepository: UserModeRepository,
    private val onboardingRepository: OnboardingRepository,
) {

    suspend operator fun invoke(): Result<Unit> =
        authRepository.deleteAccount().onSuccess { forgetAccountLocally() }

    /**
     * Runs the four cleanup steps in order, attempting every one of them.
     *
     * Deliberately best effort, and deliberately not allowed to fail the whole operation. By
     * the time this runs the account is *gone* — reporting a failure would tell the lifter
     * their data had not been removed, which is untrue, and stopping at the first problem
     * would be worse still: a Room hiccup would leave the app holding a user mode that names
     * an account the server has already destroyed, with no way back into it.
     *
     * So each step stands on its own. The realistic failures here are a DataStore write and
     * a Room transaction, neither of which has any bearing on whether the next step can run.
     *
     * **The order is chosen for what a crash leaves behind, not for tidiness.** These four
     * writes are not one transaction and cannot be — they span WorkManager, DataStore and
     * Room — so the app has to survive being killed between any two of them. The identity
     * goes first, immediately after the workers: the credentials are already gone by this
     * point, and a process death before [UserModeRepository.clearAccount] would leave the
     * next launch reading `Authenticated` over a database still full of a deleted account's
     * training, opening straight onto Home as though nothing had happened, with no session
     * left to notice otherwise. Clearing the mode first makes every partial state resolve to
     * the entry screen instead.
     *
     * The residue that remains after an interrupted wipe is then the lifter's own training,
     * on the lifter's own phone, reachable only by choosing to carry on locally — which is a
     * far better failure than an app that quietly claims to be signed in to an account that
     * no longer exists.
     */
    private suspend fun forgetAccountLocally() {
        attempt { cloudSyncStatusRepository.cancelScheduledSync() }
        attempt { userModeRepository.clearAccount() }
        attempt { onboardingRepository.clearOnboardingCompleted() }
        attempt { cloudBackupRepository.clearLocalAccountData() }
    }

    /**
     * [CancellationException] is rethrown untouched: a cancelled scope is not a failed step,
     * and swallowing it would let the rest of the cleanup run on a coroutine that is already
     * being torn down.
     */
    private suspend fun attempt(step: suspend () -> Unit) {
        try {
            step()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("SwallowedException") failure: Exception) {
            // Intentionally ignored — see forgetAccountLocally.
        }
    }
}
