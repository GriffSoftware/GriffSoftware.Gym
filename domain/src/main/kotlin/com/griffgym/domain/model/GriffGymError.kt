package com.griffgym.domain.model

/**
 * Everything that can go wrong talking to Griff Gym, in terms the app can reason about.
 *
 * `HttpException`, `IOException` and a `ResponseBody` never leave infrastructure. A
 * ViewModel deciding what to show should not have to know what a 422 is, and a screen that
 * pattern-matches on exception types from a networking library is a screen that breaks when
 * the library changes.
 */
sealed class GriffGymError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** No usable connection, DNS failure, or the request timed out. */
    class Network(cause: Throwable? = null) :
        GriffGymError("Could not reach Griff Gym.", cause)

    /** Credentials were wrong, or the session is no longer valid. */
    class Unauthorized(message: String = "Your session has expired.") :
        GriffGymError(message)

    /**
     * The server rejected the request field by field. Kept as a map so a form can put each
     * message under the input it belongs to instead of dumping them all at the top.
     */
    class Validation(
        val fieldErrors: Map<String, List<String>>,
        message: String = "Some details need fixing.",
    ) : GriffGymError(message) {

        fun firstMessageOr(fallback: String): String =
            fieldErrors.values.firstOrNull()?.firstOrNull() ?: fallback
    }

    /** A duplicate, or a record the server has already moved past. */
    class Conflict(message: String = "This has already been changed elsewhere.") :
        GriffGymError(message)

    /** The server changed a record after this device read it. Nothing was overwritten. */
    class VersionConflict(
        val expectedVersion: Int?,
        val actualVersion: Int?,
    ) : GriffGymError("This record was changed on another device.")

    /** The server failed. Not the lifter's problem and not something they can fix. */
    class Server(val statusCode: Int, message: String = "Griff Gym is having trouble.") :
        GriffGymError(message)

    /** The service is down or in maintenance. Worth retrying later. */
    class Unavailable(message: String = "Griff Gym is temporarily unavailable.") :
        GriffGymError(message)

    class Unknown(cause: Throwable? = null) :
        GriffGymError("Something went wrong.", cause)

    /**
     * Whether waiting and trying the same thing again could plausibly work. Drives the
     * background worker's retry decision, so that it backs off on a flaky connection but
     * does not hammer the server over a request it will always reject.
     */
    val isRetryable: Boolean
        get() = this is Network || this is Server || this is Unavailable
}
