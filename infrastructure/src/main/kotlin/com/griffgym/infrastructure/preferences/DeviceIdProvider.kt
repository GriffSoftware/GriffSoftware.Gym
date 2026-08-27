package com.griffgym.infrastructure.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A stable, meaningless label for this installation.
 *
 * The API accepts an optional `deviceId` on register, login and refresh so a lifter can hold
 * several live sessions at once — phone, old phone, tablet — and so signing out on one does not
 * end the others. Without it every sign-in would silently replace the last.
 *
 * Randomly generated on first use and stored, rather than derived from `ANDROID_ID`, the IMEI
 * or anything else the device knows about its owner. The server states plainly that this value
 * is never treated as a credential or as proof of anything, which makes a hardware identifier
 * pure liability: it would leave the app sending a cross-app trackable value to a server that
 * has no use for it.
 *
 * Cached in memory because it is read on every refresh, including from OkHttp's authenticator.
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    @Volatile
    private var cached: String? = null

    suspend fun deviceId(): String {
        cached?.let { return it }

        val candidate = UUID.randomUUID().toString()

        // `edit` runs under DataStore's own lock and returns the resulting snapshot, so two
        // coroutines racing here settle on one label rather than each writing its own and the
        // second replacing one the server has already seen.
        val stored = dataStore.edit { preferences ->
            if (preferences[DEVICE_ID] == null) {
                preferences[DEVICE_ID] = candidate
            }
        }

        val id = stored[DEVICE_ID] ?: candidate
        cached = id
        return id
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}
