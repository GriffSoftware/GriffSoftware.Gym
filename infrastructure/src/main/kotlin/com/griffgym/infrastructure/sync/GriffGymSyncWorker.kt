package com.griffgym.infrastructure.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.griffgym.domain.model.GriffGymError
import com.griffgym.domain.model.UserMode
import com.griffgym.domain.repository.UserModeRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Backs up whatever is waiting, whenever the phone has a connection.
 *
 * Idempotent by construction: it uploads records the server already identifies by sync id, so
 * running twice writes the same rows twice rather than creating duplicates — which matters,
 * because WorkManager will happily run it again after a process death mid-pass.
 *
 * It is also careful about what it reports back. `retry()` is for failures that waiting could
 * fix; `failure()` is for those it cannot, and would otherwise have the worker retrying a
 * rejected request until the backoff ceiling. `success()` covers the ordinary case of a
 * local-only lifter with nothing to do at all.
 */
@HiltWorker
internal class GriffGymSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val syncEngine: SyncEngine,
    private val userModeRepository: UserModeRepository,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        // A local-only lifter has no server to talk to. Not an error, and not something to
        // retry — there is simply nothing to do.
        if (userModeRepository.getUserMode() !is UserMode.Authenticated) {
            return Result.success()
        }

        return syncEngine.pushPendingChanges().fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                when {
                    error is GriffGymError && error.isRetryable -> Result.retry()

                    // The session is gone. Retrying cannot help, and the app has already been
                    // told to ask the lifter to sign in again. Local data is untouched.
                    error is GriffGymError.Unauthorized -> Result.failure()

                    else -> Result.failure()
                }
            },
        )
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK = "griffgym-sync-periodic"
        const val UNIQUE_ONE_OFF_WORK = "griffgym-sync-now"
    }
}
