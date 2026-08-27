package com.griffgym.infrastructure.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.griffgym.domain.model.AuthSession
import com.griffgym.domain.model.UserMode
import com.griffgym.domain.repository.UserModeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which of the two ways of using Griff Gym this installation is on.
 *
 * In DataStore rather than Room, for the same reason the onboarding flag is: Room is reserved
 * for training data, where every schema change has to be migrated and nothing may ever be lost.
 * A mode and an email do not belong in that story.
 *
 * Deliberately *not* derived from "is there a token?". A lifter who chose to stay local has
 * made a decision the app must respect and must not keep re-asking about, and a lifter whose
 * session expired is still an account holder — inferring the mode would confuse the two and
 * would send the second one back to the data-protection screen every time their token died.
 *
 * Kept apart from the tokens themselves, which live encrypted in their own store. This one
 * holds nothing secret: an id and an email that the lifter typed in.
 */
@Singleton
class DataStoreUserModeRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserModeRepository {

    override fun observeUserMode(): Flow<UserMode> =
        dataStore.data.map(::toUserMode).distinctUntilChanged()

    override suspend fun getUserMode(): UserMode = toUserMode(dataStore.data.first())

    override suspend fun chooseLocalOnly() {
        dataStore.edit { preferences ->
            preferences[MODE] = MODE_LOCAL_ONLY
            // Belt and braces: choosing local-only after having been signed in must not leave
            // the previous account's address behind for the next screen to read.
            preferences.remove(USER_ID)
            preferences.remove(EMAIL)
        }
    }

    override suspend fun markAuthenticated(session: AuthSession) {
        dataStore.edit { preferences ->
            preferences[MODE] = MODE_AUTHENTICATED
            preferences[USER_ID] = session.userId
            preferences[EMAIL] = session.email
        }
    }

    override suspend fun clearAccount() {
        dataStore.edit { preferences ->
            preferences.remove(MODE)
            preferences.remove(USER_ID)
            preferences.remove(EMAIL)
        }
    }

    /**
     * An `AUTHENTICATED` marker with no identity behind it is treated as [UserMode.Undecided]
     * rather than as a broken account. It cannot arise from this class — the three values are
     * written in one atomic edit — but a half-written store from a future format change, or a
     * partially restored backup, would otherwise put the app into a signed-in state it has no
     * user for. Falling back to the entry screen is recoverable; a null user id four layers up
     * is not.
     */
    private fun toUserMode(preferences: Preferences): UserMode =
        when (preferences[MODE]) {
            MODE_LOCAL_ONLY -> UserMode.LocalOnly

            MODE_AUTHENTICATED -> {
                val userId = preferences[USER_ID]
                val email = preferences[EMAIL]
                if (userId.isNullOrBlank() || email.isNullOrBlank()) {
                    UserMode.Undecided
                } else {
                    UserMode.Authenticated(userId = userId, email = email)
                }
            }

            else -> UserMode.Undecided
        }

    private companion object {
        val MODE = stringPreferencesKey("user_mode")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("user_email")

        /**
         * Stored as names, not ordinals. An ordinal survives nothing: insert a case into the
         * sealed hierarchy and every installed phone silently changes mode.
         */
        const val MODE_LOCAL_ONLY = "LOCAL_ONLY"
        const val MODE_AUTHENTICATED = "AUTHENTICATED"
    }
}
