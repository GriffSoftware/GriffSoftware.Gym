package com.griffgym.infrastructure.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.griffgym.domain.model.StrengthBlockTemplate
import com.griffgym.infrastructure.cycleRepository
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.repository.CloudBackupRepositoryImpl
import com.griffgym.infrastructure.seed.DatabaseSeeder
import com.griffgym.infrastructure.startCycleFrom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Emptying the local database must not overlap a sync pass.
 *
 * Sign-out and account deletion both cancel the WorkManager jobs before wiping Room, which is
 * necessary and not sufficient: WorkManager cancellation is asynchronous and cooperative, so a
 * pass already inside the engine keeps running until it notices, and its metadata writes are
 * free to land *after* the wipe has committed. The wipe is one Room transaction, which makes
 * it atomic but does nothing to stop another writer arriving a moment later — so a deleted
 * account could leave a remnant behind on the phone.
 *
 * The fix is that the wipe takes the engine's own sync lock. This test holds that lock and
 * shows the wipe genuinely waits for it, rather than trusting a one-line change nobody will
 * look at again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WipeHeldOffAgainstSyncTest {

    private lateinit var database: GriffGymDatabase
    private lateinit var engine: SyncEngine
    private lateinit var repository: CloudBackupRepositoryImpl

    private val clock = Clock.fixed(Instant.parse("2026-03-04T09:30:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GriffGymDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val writer = LocalStateWriter(
            database,
            database.cloudSyncDao(),
            database.syncMetadataDao(),
        )

        engine = SyncEngine(
            gateway = FakeCloudStateGateway(),
            localStateReader = LocalStateReader(database.cloudSyncDao()),
            localStateWriter = writer,
            syncMetadataDao = database.syncMetadataDao(),
            clock = clock,
        )

        repository = CloudBackupRepositoryImpl(engine, writer)

        DatabaseSeeder(database).seedIfNeeded()
        cycleRepository(database).startCycleFrom(StrengthBlockTemplate.baselineReferenceMaxes, clock)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `the wipe waits for a sync pass that has not finished yet`() = runTest {
        val passInFlight = CompletableDeferred<Unit>()

        // Stands in for a pass the engine is already running — a worker WorkManager has been
        // told to cancel but which has not stopped yet.
        val pass = launch(start = CoroutineStart.UNDISPATCHED) {
            engine.withSyncHeldOff { passInFlight.await() }
        }

        var wiped = false
        val wipe = launch {
            repository.clearLocalAccountData()
            wiped = true
        }

        runCurrent()

        assertFalse(
            "the database was emptied while a sync pass was still running",
            wiped,
        )
        assertTrue(
            "the cycle should still be there until the wipe is allowed to proceed",
            database.trainingCycleDao().getCurrent() != null,
        )

        passInFlight.complete(Unit)
        pass.join()
        wipe.join()

        assertTrue(wiped)
        assertEquals(null, database.trainingCycleDao().getCurrent())
        assertEquals(0, database.syncMetadataDao().countOutstanding())
    }
}
