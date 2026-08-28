package com.griffgym.infrastructure.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.griffgym.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the first-run flag in DataStore rather than in Room.
 *
 * Room is reserved for training data — plans, sessions, maxes — and every schema change
 * there has to be migrated. A single boolean about the state of the app itself does not
 * belong in that story, and keeping it out means onboarding shipped without touching the
 * database schema at all.
 */
@Singleton
class DataStoreOnboardingRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OnboardingRepository {

    override suspend fun isOnboardingCompleted(): Boolean =
        dataStore.data.first()[ONBOARDING_COMPLETED] == true

    override suspend fun markOnboardingCompleted() {
        dataStore.edit { it[ONBOARDING_COMPLETED] = true }
    }

    /**
     * Removed rather than written as `false`, so the preferences file ends up in the state a
     * fresh install has. The two read the same today, but leaving a key behind is how "have
     * we asked this lifter yet?" quietly becomes a third answer later on.
     */
    override suspend fun clearOnboardingCompleted() {
        dataStore.edit { it.remove(ONBOARDING_COMPLETED) }
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
