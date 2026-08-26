package com.griffgym

import android.app.Application
import com.griffgym.infrastructure.seed.DatabaseSeeder
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
 */
@HiltAndroidApp
class GriffGymApplication : Application() {

    @Inject
    lateinit var databaseSeeder: DatabaseSeeder

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { databaseSeeder.seedIfNeeded() }
    }
}
