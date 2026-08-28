package com.griffgym.presentation.account

import com.griffgym.domain.model.GriffGymError
import java.util.Locale

/** The input a message belongs under, so a form can point at the thing that is wrong. */
enum class AuthField {
    EMAIL,
    PASSWORD,
    CONFIRM_PASSWORD,
}

/**
 * A failure as a form can render it: at most one banner, plus whatever belongs under an
 * input.
 */
internal data class AuthFailure(
    /** Shown above the buttons. Null when every message found an input to sit under. */
    val message: String? = null,
    val fieldErrors: Map<AuthField, String> = emptyMap(),
)

/**
 * Which screen is asking, because the same error means different things in different
 * places — a 401 while signing in is a typo, a 401 on the account screen is an expired
 * session, and a 409 is only ever "that email is taken" during registration.
 */
internal enum class AuthContext {
    REGISTER,
    LOGIN,

    /**
     * Signing in with Google, where a 401 says nothing about a password: the token was
     * rejected, and telling a lifter their credentials were wrong when they typed none
     * would be nonsense.
     */
    GOOGLE,
    ACCOUNT,
}

/**
 * Turns anything thrown on the way to the server into something a lifter can act on.
 *
 * The rule this file exists to enforce: **nothing else is ever shown.** No status codes, no
 * exception class names, no `Throwable.message` from an unrecognised failure — a stack of
 * libraries none of which write for humans would otherwise end up on screen the first time
 * somebody's DNS hiccups. Anything not explicitly recognised falls through to one plain
 * sentence.
 *
 * The only server-authored text that reaches the screen is [GriffGymError.Validation], and
 * only because those messages are written per field for exactly this purpose.
 */
internal fun Throwable.toAuthFailure(context: AuthContext): AuthFailure = when (this) {
    is GriffGymError.Validation -> toAuthFailure()

    is GriffGymError.Network -> AuthFailure(AccountMessages.NO_CONNECTION)

    is GriffGymError.Unauthorized -> AuthFailure(
        when (context) {
            // Never "no account with that email": which half was wrong is not something the
            // app should confirm to whoever is holding the phone.
            AuthContext.LOGIN -> AccountMessages.WRONG_CREDENTIALS
            AuthContext.REGISTER -> AccountMessages.GENERIC
            AuthContext.GOOGLE -> AccountMessages.GOOGLE_REJECTED
            AuthContext.ACCOUNT -> AccountMessages.SESSION_ENDED
        },
    )

    is GriffGymError.Conflict -> AuthFailure(
        when (context) {
            AuthContext.REGISTER -> AccountMessages.EMAIL_TAKEN
            else -> AccountMessages.CHANGED_ELSEWHERE
        },
    )

    is GriffGymError.VersionConflict -> AuthFailure(AccountMessages.CHANGED_ELSEWHERE)

    is GriffGymError.Server, is GriffGymError.Unavailable ->
        AuthFailure(AccountMessages.SERVICE_TROUBLE)

    else -> AuthFailure(AccountMessages.GENERIC)
}

/**
 * The same job for a sign-in that never reached the server.
 *
 * Credential Manager fails for reasons the API layer has no vocabulary for — no Google
 * account on the phone, Play services out of date, a build with no client id — and those are
 * worth separating from "the network is down", because only one of them is worth retrying.
 * Everything past the token exchange is an ordinary API failure and falls through to
 * [toAuthFailure].
 *
 * [GoogleSignInException.Cancelled] is deliberately not handled here. A dismissed picker is
 * not a failure and never reaches a banner; the caller returns quietly instead.
 */
internal fun Throwable.toGoogleSignInFailure(): AuthFailure = when (this) {
    is GoogleSignInException.Unavailable -> AuthFailure(AccountMessages.GOOGLE_UNAVAILABLE)

    is GoogleSignInException -> AuthFailure(AccountMessages.GOOGLE_SIGN_IN_FAILED)

    else -> toAuthFailure(AuthContext.GOOGLE)
}

/**
 * Splits the server's field errors between the inputs they belong to and the banner.
 *
 * A message for a field this form does not have — the server knowing about something the
 * app does not — still has to be visible, so it is promoted to the banner rather than
 * silently dropped.
 */
private fun GriffGymError.Validation.toAuthFailure(): AuthFailure {
    val placed = mutableMapOf<AuthField, String>()
    var unplaced: String? = null

    fieldErrors.forEach { (key, messages) ->
        val message = messages.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@forEach
        val field = key.toAuthField()
        if (field != null && !placed.containsKey(field)) {
            placed[field] = message
        } else if (unplaced == null) {
            unplaced = message
        }
    }

    return AuthFailure(
        message = when {
            unplaced != null -> unplaced
            placed.isEmpty() -> firstMessageOr(AccountMessages.GENERIC)
            else -> null
        },
        fieldErrors = placed,
    )
}

/**
 * Server field names are matched loosely on purpose: `confirmPassword`, `confirm_password`
 * and `password_confirmation` are the same field, and a casing change on the backend must
 * not quietly move a message from under an input to the top of the form.
 */
private fun String.toAuthField(): AuthField? =
    when (lowercase(Locale.ROOT).filter { it.isLetter() }) {
        "email", "emailaddress" -> AuthField.EMAIL
        "password" -> AuthField.PASSWORD
        "confirmpassword", "passwordconfirmation", "passwordconfirm" -> AuthField.CONFIRM_PASSWORD
        else -> null
    }

/**
 * Every sentence the cloud features are allowed to say when something goes wrong.
 *
 * Written to answer the only question a lifter has — "is my training still there?" — before
 * anything else. None of them mention a network, a server or an account system.
 */
internal object AccountMessages {

    const val NO_CONNECTION =
        "No connection to Griff Gym. Your training data is safe on this device."

    const val WRONG_CREDENTIALS = "That email and password do not match an account."

    const val EMAIL_TAKEN = "An account already exists for this email. Sign in instead."

    const val SERVICE_TROUBLE = "Griff Gym is having trouble right now. Try again in a moment."

    const val SESSION_ENDED = "Your session has ended. Sign in again to keep backing up."

    const val CHANGED_ELSEWHERE = "Your account was changed on another device. Try again."

    const val GENERIC = "Something went wrong. Try again."

    const val GOOGLE_UNAVAILABLE = "Google sign-in is not available on this device. " +
        "Create an account with an email address instead."

    const val GOOGLE_SIGN_IN_FAILED = "Google sign-in could not be completed. Try again, " +
        "or use an email address instead."

    /**
     * The token was minted but the server would not take it — expired, or meant for a
     * different app. Nothing a lifter can fix by trying harder, so it offers the other door.
     */
    const val GOOGLE_REJECTED = "Griff Gym could not confirm your Google account. Try again, " +
        "or use an email address instead."

    /**
     * The account exists but the app could not work out what to do with the lifter's data,
     * so nothing was marked as backed up. Says what to do next rather than what failed.
     */
    const val REGISTERED_BUT_UNRESOLVED =
        "Your account was created, but Griff Gym could not finish setting it up. " +
            "Sign in when you have a connection — your training data is safe on this device."

    const val BACKUP_FAILED_REASON = "Your backup could not be completed."

    const val RESTORE_FAILED_REASON = "Your backup could not be downloaded."
}
