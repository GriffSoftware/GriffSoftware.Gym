package com.griffgym.presentation.account

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.griffgym.domain.model.AuthSession

/**
 * The auth graph's own routes. Private to the flow — the app shell never navigates to any
 * of these, it mounts [AuthNavHost] and waits for an [AuthFlowResult].
 */
object AuthRoutes {

    const val DATA_PROTECTION = "dataProtection"
    const val LOGIN = "login"
    const val REGISTER = "register"

    const val USER_ID_ARG = "userId"
    const val EMAIL_ARG = "email"

    const val BACKUP_LOCAL_DATA = "backupLocalData/{$USER_ID_ARG}/{$EMAIL_ARG}"
    const val RESTORE_CLOUD_DATA = "restoreCloudData/{$USER_ID_ARG}/{$EMAIL_ARG}"
    const val DATA_CONFLICT = "dataConflict/{$USER_ID_ARG}/{$EMAIL_ARG}"

    fun backupLocalData(session: AuthSession): String = "backupLocalData/${session.asArgs()}"

    fun restoreCloudData(session: AuthSession): String = "restoreCloudData/${session.asArgs()}"

    fun dataConflict(session: AuthSession): String = "dataConflict/${session.asArgs()}"

    /**
     * The session travels as route arguments rather than in a holder shared across the
     * graph, so a backup that is interrupted by process death comes back knowing whose
     * account it was uploading to instead of dropping the lifter back at the start.
     *
     * Safe to put in a route only because [AuthSession] carries no credentials — see its
     * documentation, which is explicit that tokens never leave infrastructure. Encoded
     * because an email address is allowed characters a path segment is not.
     */
    private fun AuthSession.asArgs(): String =
        "${Uri.encode(userId)}/${Uri.encode(email)}"
}

/**
 * Rebuilds the session a progress screen was started for.
 *
 * Throws when it is missing: the backup, restore and conflict screens have no meaning
 * without one, and failing loudly at construction beats uploading a lifter's history into
 * an account identified by an empty string.
 */
internal fun SavedStateHandle.requireAuthSession(): AuthSession {
    val userId = get<String>(AuthRoutes.USER_ID_ARG)
    val email = get<String>(AuthRoutes.EMAIL_ARG)
    require(!userId.isNullOrBlank() && !email.isNullOrBlank()) {
        "This screen can only be opened for a signed-in account."
    }
    return AuthSession(userId = userId, email = email)
}
