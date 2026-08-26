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
 * Seeding runs once here, off the main thread, and is idempotent — every screen observes
 * Room through a Flow, so the UI simply fills in as soon as the first run finishes.
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
