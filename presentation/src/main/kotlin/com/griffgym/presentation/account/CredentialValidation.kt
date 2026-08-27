package com.griffgym.presentation.account

/**
 * The checks the form runs before it bothers the server.
 *
 * These exist to save a round trip and to put the cursor next to the mistake — nothing
 * more. **The server is the authority on what a valid account is**, and the app deliberately
 * does not try to mirror its password policy: a client that "knows" the rules is a client
 * that starts refusing perfectly good credentials the day the policy changes. Anything that
 * gets past these still comes back as [com.griffgym.domain.model.GriffGymError.Validation]
 * and is shown under the same input.
 *
 * The email pattern is intentionally permissive. Real addresses are stranger than any
 * regex, so this only rules out input that could not possibly be one.
 */
internal object Credentials {

    /**
     * A floor, not the policy. Long enough that a typo in an empty field is caught here
     * rather than after a round trip; short enough that it never argues with the server.
     */
    const val MIN_PASSWORD_LENGTH = 8

    private val EMAIL_SHAPE = Regex("""^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$""")

    fun emailError(email: String): String? = when {
        email.isBlank() -> "Enter your email address"
        !EMAIL_SHAPE.matches(email.trim()) -> "Enter a valid email address"
        else -> null
    }

    fun passwordError(password: String): String? = when {
        password.isEmpty() -> "Enter a password"
        password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters"
        else -> null
    }

    /**
     * Compared verbatim: trimming here would let a stray space through to the server and
     * turn a caught typo into a password nobody can reproduce.
     */
    fun confirmationError(password: String, confirmation: String): String? = when {
        confirmation.isEmpty() -> "Repeat your password"
        confirmation != password -> "Passwords do not match"
        else -> null
    }
}
