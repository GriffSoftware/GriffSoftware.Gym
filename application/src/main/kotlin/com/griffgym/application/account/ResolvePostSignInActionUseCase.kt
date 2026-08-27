package com.griffgym.application.account

import com.griffgym.domain.model.AuthSession
import com.griffgym.domain.repository.CloudBackupRepository
import com.griffgym.domain.repository.CloudStateSummary
import com.griffgym.domain.repository.LocalTrainingDataRepository
import javax.inject.Inject

/**
 * What has to happen to the lifter's data immediately after they sign in.
 *
 * Four outcomes, decided by two questions: is there anything on this phone, and is there
 * anything in the account? Each combination is a genuinely different situation, and getting
 * one of them wrong destroys training history — which is why this is a use case with a name
 * and tests rather than an `if` inside a ViewModel.
 */
sealed interface PostSignInAction {

    /** Nothing anywhere. A brand new lifter: ask for their maxes and build cycle 1. */
    data object StartOnboarding : PostSignInAction

    /**
     * Months of training on the phone, an empty account. The migration case: upload it.
     * Nothing is regenerated and no new cycle is created — the local state *is* the state.
     */
    data object BackUpLocalData : PostSignInAction

    /** A new phone. Pull the account's history down and carry on where they left off. */
    data object RestoreCloudData : PostSignInAction

    /**
     * Two independent histories. Merging them automatically would silently rewrite one, so
     * the app refuses and asks.
     */
    data object ResolveConflict : PostSignInAction

    /** Everything already lines up. Go straight to Home. */
    data object Continue : PostSignInAction
}

class ResolvePostSignInActionUseCase @Inject constructor(
    private val cloudBackupRepository: CloudBackupRepository,
    private val localTrainingDataRepository: LocalTrainingDataRepository,
) {

    suspend operator fun invoke(@Suppress("UNUSED_PARAMETER") session: AuthSession): Result<PostSignInAction> =
        runCatching {
            val cloud = cloudBackupRepository.readCloudSummary().getOrThrow()
            val hasLocalData = localTrainingDataRepository.hasAnyTrainingData()

            when {
                cloud == CloudStateSummary.EMPTY && !hasLocalData ->
                    PostSignInAction.StartOnboarding

                cloud == CloudStateSummary.EMPTY ->
                    PostSignInAction.BackUpLocalData

                !hasLocalData ->
                    PostSignInAction.RestoreCloudData

                // Both sides hold training data. They may well be the same data — this phone
                // syncing again after a reinstall of the app but not a wipe of the database —
                // but they may equally be two unrelated histories, and nothing available here
                // can tell the two apart with enough confidence to overwrite either.
                else ->
                    PostSignInAction.ResolveConflict
            }
        }
}
