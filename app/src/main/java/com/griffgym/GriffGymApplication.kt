package com.griffgym

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.griffgym.infrastructure.seed.DatabaseSeeder
import com.griffgym.infrastructure.sync.SyncBootstrap
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Composition root.
 *
 * Seeding the exercise catalogue runs once here, off the main thread, and is idempotent.
 * The lifter's own data — reference maxes and the training block — is not seeded: it comes
 * from first-run setup, and generating a program inserts any catalogue entry it still
 * needs, so nothing depends on this having finished first.
 *
 * Cloud synchronisation is started the same way: off the main thread, and only doing anything
 * for a lifter who actually has an account. Nothing here blocks the first frame — Home is
 * drawn from Room, and whether a backup is in flight is not something the lifter waits on.
 */
@HiltAndroidApp
class GriffGymApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var databaseSeeder: DatabaseSeeder

    /**
     * WorkManager builds workers itself, so the sync worker's dependencies have to be handed
     * to it through Hilt's factory rather than a constructor the app controls.
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncBootstrap: SyncBootstrap

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            databaseSeeder.seedIfNeeded()
            syncBootstrap.onApplicationStart()
        }
    }
}
