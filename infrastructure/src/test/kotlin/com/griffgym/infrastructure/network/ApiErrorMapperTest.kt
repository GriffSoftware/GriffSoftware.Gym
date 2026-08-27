package com.griffgym.infrastructure.network

import com.griffgym.domain.model.GriffGymError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Every failure the API can hand back, turned into something the app can act on.
 *
 * Worth its own test because the distinctions are load-bearing: a retryable failure and a
 * permanent one look identical over the wire until the `ProblemDetails` document is read, and
 * the background worker's back-off is built on telling them apart.
 */
class ApiErrorMapperTest {

    private val mapper = ApiErrorMapper(Json { ignoreUnknownKeys = true; explicitNulls = false })

    @Test
    fun `400 with a field map becomes Validation with the messages intact`() {
        val error = mapper.map(
            httpException(
                400,
                """
                {
                  "title": "Validation failed",
                  "status": 400,
                  "errors": {
                    "program.weeks[0].workouts[0].plannedSets[1].rpeMin":
                      ["'Rpe Min' must be between 1.0 and 10.0 in steps of 0.5."]
                  }
                }
                """.trimIndent(),
            ),
        )

        val validation = error as GriffGymError.Validation
        assertEquals(
            "'Rpe Min' must be between 1.0 and 10.0 in steps of 0.5.",
            validation.firstMessageOr("nothing"),
        )
        assertFalse(validation.isRetryable)
    }

    @Test
    fun `401 becomes Unauthorized even with no body at all`() {
        // The JWT middleware refuses the request before any controller runs, so there is
        // nothing to parse. It still has to arrive as a session problem rather than as Unknown.
        val error = mapper.map(httpException(401, body = ""))

        assertTrue(error is GriffGymError.Unauthorized)
        assertEquals("Your session has expired.", error.message)
    }

    @Test
    fun `409 carrying both versions is a VersionConflict, not a duplicate`() {
        val error = mapper.map(
            httpException(
                409,
                """
                {
                  "title": "Version conflict",
                  "status": 409,
                  "detail": "Workout has moved on: expected version 4, found 6.",
                  "expectedVersion": 4,
                  "actualVersion": 6
                }
                """.trimIndent(),
            ),
        )

        val conflict = error as GriffGymError.VersionConflict
        assertEquals(4, conflict.expectedVersion)
        assertEquals(6, conflict.actualVersion)
    }

    @Test
    fun `409 without versions is a plain Conflict`() {
        val error = mapper.map(
            httpException(409, problemJson(409, "Conflict", "That cycle number is taken.")),
        )

        assertTrue(error is GriffGymError.Conflict)
        assertFalse(error is GriffGymError.VersionConflict)
        assertEquals("That cycle number is taken.", error.message)
    }

    @Test
    fun `422 is a Conflict when the rules simply forbid it`() {
        val error = mapper.map(
            httpException(
                422,
                problemJson(422, "Unprocessable request", "That cycle is already completed."),
            ),
        )

        assertTrue(error is GriffGymError.Conflict)
        assertEquals("That cycle is already completed.", error.message)
    }

    @Test
    fun `422 that names fields is a Validation`() {
        val error = mapper.map(
            httpException(
                422,
                """
                {
                  "title": "Unprocessable request",
                  "status": 422,
                  "errors": { "actualReps": ["A completed set needs at least one rep."] }
                }
                """.trimIndent(),
            ),
        )

        val validation = error as GriffGymError.Validation
        assertEquals(
            listOf("A completed set needs at least one rep."),
            validation.fieldErrors["actualReps"],
        )
    }

    @Test
    fun `500 keeps its status code and is retryable`() {
        val error = mapper.map(
            httpException(
                500,
                problemJson(500, "Unexpected error", "Something went wrong. It has been logged."),
            ),
        )

        val server = error as GriffGymError.Server
        assertEquals(500, server.statusCode)
        assertEquals("Something went wrong. It has been logged.", server.message)
        assertTrue(server.isRetryable)
    }

    @Test
    fun `503 is Unavailable rather than a generic server failure`() {
        val error = mapper.map(httpException(503, body = ""))

        assertTrue(error is GriffGymError.Unavailable)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `429 is retryable, because backing off is exactly the right response`() {
        val error = mapper.map(
            httpException(429, problemJson(429, "Too many requests", "Slow down.")),
        )

        assertTrue(error is GriffGymError.Unavailable)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `404 is not retryable, because the record is gone or was never ours`() {
        val error = mapper.map(httpException(404, problemJson(404, "Not found", "No such cycle.")))

        assertTrue(error is GriffGymError.Conflict)
        assertFalse(error.isRetryable)
    }

    @Test
    fun `an error body that is not JSON does not become a second failure`() {
        val error = mapper.map(httpException(502, "<html><body>Bad gateway</body></html>"))

        assertTrue(error is GriffGymError.Server)
        assertEquals(502, (error as GriffGymError.Server).statusCode)
        assertNull(mapper.parseProblemDetails("<html>"))
    }

    @Test
    fun `every connection failure is Network, whichever IOException it arrives as`() {
        listOf(
            UnknownHostException("api.griffgym.invalid"),
            SocketTimeoutException("timeout"),
            IOException("unexpected end of stream"),
        ).forEach { cause ->
            val error = mapper.map(cause)
            assertTrue("$cause should map to Network", error is GriffGymError.Network)
            assertSame(cause, error.cause)
            assertTrue(error.isRetryable)
        }
    }

    @Test
    fun `an error that has already been mapped is not re-wrapped`() {
        val original = GriffGymError.VersionConflict(expectedVersion = 4, actualVersion = 6)

        assertSame(original, mapper.map(original))
    }

    /**
     * Swallowing cancellation into a `Result` would break structured concurrency: a ViewModel
     * whose scope had already gone would carry on and update state that no longer exists.
     */
    @Test(expected = CancellationException::class)
    fun `safeApiCall rethrows cancellation instead of reporting it as a failure`() = runBlocking {
        safeApiCall<Unit>(mapper) { throw CancellationException("scope closed") }
        Unit
    }

    @Test
    fun `safeApiCall reports anything else as a GriffGymError`() = runBlocking {
        val result = safeApiCall<Unit>(mapper) { throw UnknownHostException("no dns") }

        assertTrue(result.exceptionOrNull() is GriffGymError.Network)
    }

    private fun httpException(code: Int, body: String): HttpException =
        HttpException(
            Response.error<Unit>(code, body.toResponseBody("application/problem+json".toMediaType())),
        )
}
