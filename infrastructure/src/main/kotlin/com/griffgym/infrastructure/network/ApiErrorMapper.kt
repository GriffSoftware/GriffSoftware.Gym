package com.griffgym.infrastructure.network

import com.griffgym.domain.model.GriffGymError
import com.griffgym.infrastructure.network.dto.ProblemDetailsDto
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.net.HttpURLConnection.HTTP_UNAVAILABLE
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place an HTTP failure becomes something the app can reason about.
 *
 * `HttpException`, `IOException` and a `ResponseBody` stop here. Above this line a failure is a
 * [GriffGymError] and nothing else, so a ViewModel never has to know what a 422 is and a
 * screen cannot break because the networking library changed its exception hierarchy.
 *
 * Reading the error body is deliberate work rather than a convenience: the difference between
 * a 409 that means "somebody else wrote first, re-read and merge" and a 409 that means "that
 * email is taken" lives only in the `ProblemDetails` document, and throwing it away would
 * leave the sync engine unable to tell a recoverable conflict from a permanent one.
 */
@Singleton
internal class ApiErrorMapper @Inject constructor(private val json: Json) {

    fun map(throwable: Throwable): GriffGymError = when (throwable) {
        // Already mapped somewhere below — most likely by a repository that translated a
        // response body itself. Re-wrapping it would bury the specific error under Unknown.
        is GriffGymError -> throwable

        is HttpException -> fromHttp(throwable.code(), readProblem(throwable))

        // Covers UnknownHostException, SocketTimeoutException, ConnectException and the SSL
        // failures, all of which are IOException. They are one thing to a lifter — "it could
        // not reach Griff Gym" — and separating them would only produce messages nobody can
        // act on differently.
        is IOException -> GriffGymError.Network(throwable)

        else -> GriffGymError.Unknown(throwable)
    }

    /**
     * Maps a status code and its problem document.
     *
     * Public in its own right because a response can be a failure without an exception having
     * been thrown — an `activeWorkout()` call that returns `Response` gets its status code
     * handed to it rather than raised.
     */
    fun fromHttp(code: Int, problem: ProblemDetailsDto?): GriffGymError = when {
        code == HTTP_BAD_REQUEST -> validation(problem)

        // 403 never comes from this API by design — somebody else's record answers 404, not
        // "forbidden", so that a list of GUIDs cannot be used to enumerate other accounts —
        // but a proxy or gateway in front of it can produce one, and it means the same thing
        // to the lifter: these credentials will not do.
        code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN ->
            GriffGymError.Unauthorized(problem.describe(SESSION_EXPIRED))

        // "Gone, or never yours" — the API answers both the same way on purpose. Neither is
        // retryable and neither is the lifter's fault, so it lands on Conflict rather than
        // Network or Server, and the sync engine drops the record instead of retrying forever.
        code == HTTP_NOT_FOUND -> GriffGymError.Conflict(problem.describe(NOT_FOUND))

        code == HTTP_CONFLICT -> conflict(problem)

        // Well-formed, but the rules forbid it: completing a cycle twice, logging into a
        // finished session. FluentValidation can also surface a rule failure here, in which
        // case the field map is worth keeping.
        code == HTTP_UNPROCESSABLE_ENTITY ->
            if (problem?.errors.isNullOrEmpty()) {
                GriffGymError.Conflict(problem.describe(UNPROCESSABLE))
            } else {
                validation(problem)
            }

        // Rate limited. Retryable rather than fatal, which is exactly what Unavailable means
        // to the background worker's back-off.
        code == HTTP_TOO_MANY_REQUESTS -> GriffGymError.Unavailable(problem.describe(RATE_LIMITED))

        code == HTTP_UNAVAILABLE -> GriffGymError.Unavailable(problem.describe(UNAVAILABLE))

        code >= HTTP_INTERNAL_ERROR -> GriffGymError.Server(code, problem.describe(SERVER))

        else -> GriffGymError.Unknown(IllegalStateException("Unhandled HTTP status $code."))
    }

    /**
     * Parses a `ProblemDetails` body, tolerating everything an error body can actually be.
     *
     * A 401 raised by the JWT middleware never reaches the exception handler and so has no
     * body at all; a captive portal answers with HTML; a truncated response is not JSON. All
     * three are normal, none of them is a reason to fail while failing.
     */
    fun parseProblemDetails(body: String?): ProblemDetailsDto? {
        if (body.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<ProblemDetailsDto>(body) }.getOrNull()
    }

    private fun readProblem(exception: HttpException): ProblemDetailsDto? =
        parseProblemDetails(runCatching { exception.response()?.errorBody()?.string() }.getOrNull())

    /**
     * Keeps the field map so a form can put each message under the input it belongs to. An
     * empty map is still a validation failure — the server rejected the request as malformed
     * without naming a field, and calling that "unknown" would lose that.
     */
    private fun validation(problem: ProblemDetailsDto?): GriffGymError.Validation =
        GriffGymError.Validation(
            fieldErrors = problem?.errors.orEmpty(),
            message = problem.describe(VALIDATION),
        )

    private fun conflict(problem: ProblemDetailsDto?): GriffGymError {
        val expected = problem?.expectedVersion
        val actual = problem?.actualVersion

        // Both numbers present means optimistic concurrency, not a duplicate: the record moved
        // on and the write was refused rather than applied. The caller can re-read at
        // `actualVersion`, merge and try again — which is only possible if that distinction
        // survives this method.
        return if (expected != null || actual != null) {
            GriffGymError.VersionConflict(expectedVersion = expected, actualVersion = actual)
        } else {
            GriffGymError.Conflict(problem.describe(CONFLICT))
        }
    }

    /** Named apart from [ProblemDetailsDto.messageOr] so the null case is visible at the call site. */
    private fun ProblemDetailsDto?.describe(fallback: String): String =
        this?.messageOr(fallback) ?: fallback

    private companion object {
        // Not in java.net.HttpURLConnection, which predates both.
        const val HTTP_UNPROCESSABLE_ENTITY = 422
        const val HTTP_TOO_MANY_REQUESTS = 429

        const val SESSION_EXPIRED = "Your session has expired."
        const val NOT_FOUND = "That record is no longer on the server."
        const val VALIDATION = "Some details need fixing."
        const val CONFLICT = "This has already been changed elsewhere."
        const val UNPROCESSABLE = "That is not something Griff Gym can do right now."
        const val RATE_LIMITED = "Too many attempts. Give it a minute."
        const val UNAVAILABLE = "Griff Gym is temporarily unavailable."
        const val SERVER = "Griff Gym is having trouble."
    }
}

/**
 * Runs an API call and reports failure as a value.
 *
 * Every call goes through here. A `Result` rather than a thrown exception because a failed
 * backup is an expected outcome for an offline-first app in a basement gym, not an exceptional
 * one, and because the compiler then makes the caller acknowledge it.
 *
 * [CancellationException] is rethrown untouched. Swallowing it into a `Result.failure` would
 * break structured concurrency: a ViewModel whose scope had already been cancelled would carry
 * on, and would try to update state that no longer exists.
 */
internal suspend fun <T> safeApiCall(
    errorMapper: ApiErrorMapper,
    block: suspend () -> T,
): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: Exception) {
        Result.failure(errorMapper.map(exception))
    }
