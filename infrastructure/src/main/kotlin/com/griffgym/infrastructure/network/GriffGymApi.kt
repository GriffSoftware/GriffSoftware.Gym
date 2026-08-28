package com.griffgym.infrastructure.network

import com.griffgym.infrastructure.network.dto.ApplicationStateResponseDto
import com.griffgym.infrastructure.network.dto.AuthenticationResponseDto
import com.griffgym.infrastructure.network.dto.CompleteCycleRequestDto
import com.griffgym.infrastructure.network.dto.CreateCycleRequestDto
import com.griffgym.infrastructure.network.dto.CreateWorkoutRequestDto
import com.griffgym.infrastructure.network.dto.CycleResponseDto
import com.griffgym.infrastructure.network.dto.CycleSummaryResponseDto
import com.griffgym.infrastructure.network.dto.ExerciseResponseDto
import com.griffgym.infrastructure.network.dto.FinishWorkoutRequestDto
import com.griffgym.infrastructure.network.dto.GoogleLoginRequestDto
import com.griffgym.infrastructure.network.dto.LogSetRequestDto
import com.griffgym.infrastructure.network.dto.LoginRequestDto
import com.griffgym.infrastructure.network.dto.LogoutRequestDto
import com.griffgym.infrastructure.network.dto.PagedResponseDto
import com.griffgym.infrastructure.network.dto.ReferenceMaxResponseDto
import com.griffgym.infrastructure.network.dto.RefreshRequestDto
import com.griffgym.infrastructure.network.dto.RegisterRequestDto
import com.griffgym.infrastructure.network.dto.UpdateCycleProgressRequestDto
import com.griffgym.infrastructure.network.dto.UpdateReferenceMaxRequestDto
import com.griffgym.infrastructure.network.dto.UpdateWorkoutRequestDto
import com.griffgym.infrastructure.network.dto.UserResponseDto
import com.griffgym.infrastructure.network.dto.WorkoutResponseDto
import com.griffgym.infrastructure.network.dto.WorkoutSummaryResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Every endpoint Griff Gym talks to, in one interface.
 *
 * One interface rather than five, because the API is one service with about two dozen
 * operations and splitting it would buy nothing but five Retrofit proxies and five bindings to
 * keep in step. Should this grow a second bounded context, that is the moment to split it.
 *
 * No method takes an `Authorization` argument. The header is added by
 * [com.griffgym.infrastructure.network.interceptor.AuthorizationInterceptor] and refreshed by
 * [com.griffgym.infrastructure.network.auth.TokenAuthenticator]; a call site that passed one by
 * hand would be the one call site that never gets a rotated token.
 *
 * These methods throw. A `retrofit2.HttpException` on any non-2xx, an `IOException` on
 * anything below that — which is why nothing calls them directly. Everything goes through
 * [safeApiCall], which is where those become a
 * [com.griffgym.domain.model.GriffGymError].
 */
internal interface GriffGymApi {

    // ---------------------------------------------------------------------------------------
    // Auth
    // ---------------------------------------------------------------------------------------

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequestDto): AuthenticationResponseDto

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): AuthenticationResponseDto

    /**
     * Signs in with a Google ID token, creating the account the first time that address is
     * seen. One endpoint for both, so the app never has to ask a lifter which of the two they
     * meant.
     */
    @POST("api/v1/auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequestDto): AuthenticationResponseDto

    /**
     * Exchanges a refresh token for a new pair. The token presented is retired in the same
     * breath: the new [AuthenticationResponseDto.refreshToken] must be persisted before
     * anything else is done with the response, because presenting the old one again is read as
     * theft and revokes every session on the account.
     */
    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequestDto): AuthenticationResponseDto

    /** 204, and deliberately anonymous so an expired access token cannot trap a refresh token. */
    @POST("api/v1/auth/logout")
    suspend fun logout(@Body request: LogoutRequestDto)

    @POST("api/v1/auth/logout-all")
    suspend fun logoutAll()

    @GET("api/v1/users/me")
    suspend fun currentUser(): UserResponseDto

    // ---------------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------------

    @GET("api/v1/state")
    suspend fun applicationState(): ApplicationStateResponseDto

    // ---------------------------------------------------------------------------------------
    // Reference maxes and exercises
    // ---------------------------------------------------------------------------------------

    @GET("api/v1/reference-maxes")
    suspend fun referenceMaxes(): List<ReferenceMaxResponseDto>

    /** [lift] is `Squat`, `BenchPress` or `Deadlift` — see `LiftTypeDto.routeValue`. */
    @PUT("api/v1/reference-maxes/{lift}")
    suspend fun updateReferenceMax(
        @Path("lift") lift: String,
        @Body request: UpdateReferenceMaxRequestDto,
    ): ReferenceMaxResponseDto

    @GET("api/v1/exercises")
    suspend fun exercises(): List<ExerciseResponseDto>

    // ---------------------------------------------------------------------------------------
    // Cycles
    // ---------------------------------------------------------------------------------------

    @GET("api/v1/cycles")
    suspend fun cycles(): List<CycleSummaryResponseDto>

    @GET("api/v1/cycles/{cycleId}")
    suspend fun cycle(@Path("cycleId") cycleId: String): CycleResponseDto

    /**
     * Idempotent by [CreateCycleRequestDto.id]: a retry after a timeout answers 200 with the
     * cycle that already exists rather than 201 and a second copy of the same six weeks. Both
     * carry the same body, so a caller that only wants the cycle need not care which it got.
     */
    @POST("api/v1/cycles")
    suspend fun createCycle(@Body request: CreateCycleRequestDto): CycleResponseDto

    @POST("api/v1/cycles/{cycleId}/complete")
    suspend fun completeCycle(
        @Path("cycleId") cycleId: String,
        @Body request: CompleteCycleRequestDto,
    ): CycleResponseDto

    @PUT("api/v1/cycles/{cycleId}/progress")
    suspend fun updateCycleProgress(
        @Path("cycleId") cycleId: String,
        @Body request: UpdateCycleProgressRequestDto,
    ): CycleResponseDto

    // ---------------------------------------------------------------------------------------
    // Workouts
    // ---------------------------------------------------------------------------------------

    @GET("api/v1/workouts")
    suspend fun workoutHistory(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
        @Query("cycleId") cycleId: String? = null,
        @Query("status") status: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): PagedResponseDto<WorkoutSummaryResponseDto>

    /**
     * Returns `Response` rather than a body because "nothing is running" is answered with a
     * **204 and no body at all**. A declared body type would make Retrofit fail that perfectly
     * ordinary case with a null-body error.
     */
    @GET("api/v1/workouts/active")
    suspend fun activeWorkout(): Response<WorkoutResponseDto>

    @GET("api/v1/workouts/{sessionId}")
    suspend fun workout(@Path("sessionId") sessionId: String): WorkoutResponseDto

    /** Idempotent by [CreateWorkoutRequestDto.id], exactly as cycle creation is. */
    @POST("api/v1/workouts")
    suspend fun createWorkout(@Body request: CreateWorkoutRequestDto): WorkoutResponseDto

    @PUT("api/v1/workouts/{sessionId}")
    suspend fun updateWorkout(
        @Path("sessionId") sessionId: String,
        @Body request: UpdateWorkoutRequestDto,
    ): WorkoutResponseDto

    /** The hot path of the whole product: one set, the moment the lifter finishes it. */
    @PUT("api/v1/workouts/{sessionId}/sets/{setId}")
    suspend fun logSet(
        @Path("sessionId") sessionId: String,
        @Path("setId") setId: String,
        @Body request: LogSetRequestDto,
    ): WorkoutResponseDto

    @POST("api/v1/workouts/{sessionId}/complete")
    suspend fun completeWorkout(
        @Path("sessionId") sessionId: String,
        @Body request: FinishWorkoutRequestDto,
    ): WorkoutResponseDto

    @POST("api/v1/workouts/{sessionId}/cancel")
    suspend fun cancelWorkout(
        @Path("sessionId") sessionId: String,
        @Body request: FinishWorkoutRequestDto,
    ): WorkoutResponseDto
}
