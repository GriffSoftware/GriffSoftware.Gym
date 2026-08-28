package com.griffgym.application

import com.griffgym.application.account.GetStartupDestinationUseCase
import com.griffgym.application.account.GetUserModeUseCase
import com.griffgym.application.account.RestoreSessionUseCase
import com.griffgym.application.account.StartupDestination
import com.griffgym.application.onboarding.GetAppInitializationStateUseCase
import com.griffgym.domain.model.AuthSession
import com.griffgym.domain.model.UserMode
import com.griffgym.domain.repository.AuthRepository
import com.griffgym.domain.repository.UserModeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the app opens, and — more to the point — where it must never open.
 *
 * The expensive mistake here is showing first-run setup to somebody who already has a training
 * block: they would be asked for maxes they entered months ago, and accepting would generate a
 * cycle 1 on top of the one they are halfway through.
 */
class GetStartupDestinationUseCaseTest {

    @Test
    fun `a fresh install is asked where its data should live`() = runTest {
        val destination = resolve(mode = UserMode.Undecided, hasTrainingData = false)

        assertEquals(StartupDestination.ChooseDataMode, destination)
    }

    @Test
    fun `an existing lifter is asked too, before anything else happens`() = runTest {
        // Updating the app is the first time this lifter has ever been offered a backup, and
        // they are the one with the most to lose. Their data is untouched either way.
        val destination = resolve(mode = UserMode.Undecided, hasTrainingData = true)

        assertEquals(StartupDestination.ChooseDataMode, destination)
    }

    @Test
    fun `a local lifter with no data goes to first run setup`() = runTest {
        val destination = resolve(mode = UserMode.LocalOnly, hasTrainingData = false)

        assertEquals(StartupDestination.Onboarding, destination)
    }

    @Test
    fun `a local lifter who already trains goes straight to the app`() = runTest {
        val destination = resolve(mode = UserMode.LocalOnly, hasTrainingData = true)

        assertEquals(StartupDestination.Ready, destination)
    }

    @Test
    fun `a signed-in lifter opens the app, not a login screen`() = runTest {
        val destination = resolve(
            mode = UserMode.Authenticated("user-1", "lifter@example.com"),
            hasTrainingData = true,
        )

        assertEquals(StartupDestination.Ready, destination)
    }

    @Test
    fun `a signed-in lifter whose stored session is unreadable still opens the app`() = runTest {
        // Their training is in Room and it is theirs. A token that cannot be read is a prompt
        // to sign in again, not a locked door.
        val destination = resolve(
            mode = UserMode.Authenticated("user-1", "lifter@example.com"),
            hasTrainingData = true,
            sessionRestoreFails = true,
        )

        assertEquals(StartupDestination.Ready, destination)
    }

    // -----------------------------------------------------------------------------------------

    private suspend fun resolve(
        mode: UserMode,
        hasTrainingData: Boolean,
        sessionRestoreFails: Boolean = false,
    ): StartupDestination {
        val referenceMaxes = if (hasTrainingData) {
            FakeReferenceMaxRepository()
        } else {
            FakeReferenceMaxRepository(initial = emptyList())
        }

        val programs = if (hasTrainingData) {
            FakeTrainingProgramRepository()
        } else {
            FakeTrainingProgramRepository.empty()
        }

        return GetStartupDestinationUseCase(
            getUserMode = GetUserModeUseCase(FakeUserModeRepository(mode)),
            restoreSession = RestoreSessionUseCase(StubAuthRepository(sessionRestoreFails)),
            getAppInitializationState = GetAppInitializationStateUseCase(
                // Deliberately not pre-marked: the point is that an installation holding
                // training data is recognised from the data itself, not from a flag that did
                // not exist when it was created.
                onboardingRepository = FakeOnboardingRepository(completed = false),
                referenceMaxRepository = referenceMaxes,
                trainingProgramRepository = programs,
                workoutSessionRepository = FakeWorkoutSessionRepository(),
            ),
        ).invoke()
    }
}


private class StubAuthRepository(private val failRestore: Boolean) : AuthRepository {
    override fun observeSession(): Flow<AuthSession?> = flowOf(null)
    override suspend fun register(email: String, password: String) =
        Result.failure<AuthSession>(UnsupportedOperationException())
    override suspend fun login(email: String, password: String) =
        Result.failure<AuthSession>(UnsupportedOperationException())
    override suspend fun loginWithGoogle(idToken: String) =
        Result.failure<AuthSession>(UnsupportedOperationException())
    override suspend fun logout(): Result<Unit> = Result.success(Unit)
    override suspend fun deleteAccount(): Result<Unit> =
        Result.failure(UnsupportedOperationException())
    override suspend fun restoreSession(): AuthSession? =
        if (failRestore) error("token unreadable") else AuthSession("user-1", "lifter@example.com")
    override fun observeSessionExpired(): Flow<Boolean> = flowOf(false)
    override suspend fun acknowledgeSessionExpired() = Unit
}
