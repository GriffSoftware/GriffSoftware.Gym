package com.griffgym.presentation.account

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * The one step of signing in with Google that has to happen on the device.
 *
 * It returns a **string** — the raw ID token — and nothing else, which is the whole point of
 * the abstraction. Credential Manager is an Android UI API: it needs an Activity, it shows a
 * bottom sheet, and it has its own exception hierarchy. None of that can be allowed past this
 * interface, because everything downstream of it ([com.griffgym.application.account
 * .GoogleLoginUseCase], the repository, the domain) is framework-free by design and stays
 * that way as long as what crosses the boundary is a token.
 *
 * It also has to live in `:presentation` rather than `:infrastructure`, where a platform
 * integration would normally go: the call needs a `Context`, which `:domain` and
 * `:application` cannot name, and `:presentation` — the module that has one — does not depend
 * on `:infrastructure`. The interface exists as much for tests as for layering: a JVM unit
 * test cannot run Credential Manager, so a ViewModel test fakes this the same way it fakes a
 * repository.
 *
 * The `Context` is a **parameter, not a field**. A ViewModel outlives the Activity that
 * created it, so an Activity held in one is a leaked window; passing it at the call site
 * means the reference lives only as long as the call.
 */
interface GoogleSignInLauncher {

    /**
     * Shows Google's account picker and returns the ID token it mints for this app.
     *
     * [context] must be an Activity context — the picker is a UI. In Griff Gym that is
     * `LocalContext.current`, read inside a composable, because the app has exactly one
     * Activity.
     *
     * Failures arrive as a [GoogleSignInException]; a lifter who simply dismissed the picker
     * is [GoogleSignInException.Cancelled], which is not an error and must not be shown as
     * one.
     */
    suspend fun requestIdToken(context: Context): Result<String>
}

/**
 * Why a Google sign-in did not produce a token.
 *
 * Three cases rather than one because they call for three different responses: say nothing,
 * say "not on this device", or say "try again". Credential Manager's own exceptions stop
 * here.
 */
sealed class GoogleSignInException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** The picker was dismissed. A decision, not a failure — the screen says nothing. */
    class Cancelled : GoogleSignInException("The lifter dismissed the Google account picker.")

    /**
     * There is nothing to sign in with: no Google account on the device, no Play services, or
     * a build with no client id configured.
     */
    class Unavailable(
        message: String,
        cause: Throwable? = null,
    ) : GoogleSignInException(message, cause)

    /** Anything else Credential Manager threw. Worth retrying. */
    class Failed(cause: Throwable) : GoogleSignInException("Google sign-in failed.", cause)
}

/**
 * The OAuth 2.0 **web** client id the ID token is minted for.
 *
 * Qualified because it is a bare `String` in the graph, and declared here — beside its only
 * consumer — rather than in `:infrastructure`, which owns the build configuration but cannot
 * see this module. `:app` depends on both and supplies the binding; see
 * `com.griffgym.di.GoogleSignInModule`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GoogleWebClientId

/**
 * Credential Manager, and the retired `GoogleSignInClient` nowhere in sight.
 *
 * [GetSignInWithGoogleOption] rather than `GetGoogleIdOption`: this is an explicit button, so
 * the lifter is asking to pick an account, and the branded flow is the one that offers every
 * account on the device instead of silently filtering to previously-authorised ones.
 *
 * [serverClientId] is the *web* client id on purpose. It sets the audience of the returned
 * token, and a token minted for the Android client is one the backend is right to reject.
 *
 * The manager is created per call from the Activity context rather than held: it is a thin
 * handle, and the alternative is this class owning a `Context`.
 */
@Singleton
internal class CredentialManagerGoogleSignInLauncher @Inject constructor(
    @param:GoogleWebClientId private val serverClientId: String,
) : GoogleSignInLauncher {

    override suspend fun requestIdToken(context: Context): Result<String> {
        // An unconfigured build would otherwise reach Google and come back with something
        // unreadable. Failing here says what is actually wrong.
        if (serverClientId.isBlank()) {
            return Result.failure(
                GoogleSignInException.Unavailable(
                    "No Google web client id is configured in this build.",
                ),
            )
        }

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()

        return try {
            val response = CredentialManager.create(context).getCredential(context, request)
            Result.success(response.credential.extractGoogleIdToken())
        } catch (cancellation: CancellationException) {
            // The composable left the composition, not the lifter tapping "cancel". Structured
            // concurrency has to see this one; swallowing it into a Result would break it.
            throw cancellation
        } catch (dismissed: GetCredentialCancellationException) {
            Result.failure(GoogleSignInException.Cancelled())
        } catch (nothingToOffer: NoCredentialException) {
            Result.failure(
                GoogleSignInException.Unavailable(
                    "No Google account is available on this device.",
                    nothingToOffer,
                ),
            )
        } catch (noProvider: GetCredentialProviderConfigurationException) {
            // No credential provider at all: a device without Play services, or an emulator
            // image without it. Worth its own answer, because "try again" never helps.
            Result.failure(
                GoogleSignInException.Unavailable(
                    "No credential provider is available on this device.",
                    noProvider,
                ),
            )
        } catch (failure: Exception) {
            // Deliberately broad. This is a boundary around Play services, and the entry
            // screen recovering with a message is always better than the app dying on the
            // one screen a lifter has no way past.
            Result.failure(GoogleSignInException.Failed(failure))
        }
    }
}

/**
 * Credential Manager answers with an opaque credential; only its type says what it holds.
 *
 * Anything else is a wiring mistake — a second option added to the request, or a Play
 * services version answering something new — and is treated as a failure rather than
 * squeezed into a token.
 */
private fun androidx.credentials.Credential.extractGoogleIdToken(): String {
    val isGoogleIdToken = this is CustomCredential &&
        type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL

    check(isGoogleIdToken) { "Unexpected credential type '$type' from Credential Manager." }

    return GoogleIdTokenCredential.createFrom((this as CustomCredential).data).idToken
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class GoogleSignInLauncherModule {

    @Binds
    @Singleton
    abstract fun bindGoogleSignInLauncher(
        impl: CredentialManagerGoogleSignInLauncher,
    ): GoogleSignInLauncher
}
