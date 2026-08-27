package com.griffgym.infrastructure.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.griffgym.domain.model.AuthSession
import com.griffgym.domain.model.GriffGymError
import com.griffgym.domain.model.StrengthBlockTemplate
import com.griffgym.domain.model.UserMode
import com.griffgym.domain.repository.UserModeRepository
import com.griffgym.infrastructure.cycleRepository
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.entity.SyncEntityType
import com.griffgym.infrastructure.database.entity.SyncState
import com.griffgym.infrastructure.seed.DatabaseSeeder
import com.griffgym.infrastructure.startCycleFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The background worker's four possible answers, and why each one is the right one.
 *
 * WorkManager acts on what it is told: `retry()` reschedules with backoff, `failure()` gives up
 * for good, `success()` means done. Getting the mapping wrong is expensive in a way that is
 * invisible — a lifter would simply, quietly, stop being backed up.
 */
@RunWith(RobolectricTestRunner::class)
class GriffGymSyncWorkerTest {

    private lateinit var database: GriffGymDatabase
    private lateinit var gateway: FakeCloudStateGateway
    private lateinit var engine: SyncEngine
    private lateinit var userMode: FakeUserModeRepository

    private val clock = Clock.fixed(Instant.parse("2026-03-04T09:30:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GriffGymDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        gateway = FakeCloudStateGateway()
        engine = SyncEngine(
            gateway = gateway,
            localStateReader = LocalStateReader(database.cloudSyncDao()),
            localStateWriter = LocalStateWriter(
                database,
                database.cloudSyncDao(),
                database.syncMetadataDao(),
            ),
            syncMetadataDao = database.syncMetadataDao(),
            clock = clock,
        )
        userMode = FakeUserModeRepository(UserMode.Authenticated("user-1", "lifter@example.com"))

        DatabaseSeeder(database).seedIfNeeded()
        cycleRepository(database).startCycleFrom(StrengthBlockTemplate.baselineReferenceMaxes, clock)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a local-only lifter has nothing to do, and that is a success`() = runTest {
        userMode.mode = UserMode.LocalOnly
        markCyclePending()

        val result = runWorker()

        // Not a failure and not a retry: there is no server, by the lifter's own choice, and
        // retrying forever would wake the phone up to discover that over and over.
        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(gateway.uploadedCycleIds.isEmpty())
    }

    @Test
    fun `pending work is sent and the record is marked as reaching the server`() = runTest {
        val cycleSyncId = markCyclePending()

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(gateway.uploadedCycleIds.contains(cycleSyncId))
        assertEquals(0, database.syncMetadataDao().countOutstanding())
    }

    @Test
    fun `a dropped connection asks WorkManager to try again later`() = runTest {
        val cycleSyncId = markCyclePending()
        gateway.failures[cycleSyncId] = GriffGymError.Network()

        val result = runWorker()

        // Retryable, so the exponential backoff gets its chance rather than the lifter's
        // training sitting on the phone until they next open the app.
        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(
            SyncState.FAILED,
            database.syncMetadataDao().get(SyncEntityType.TRAINING_CYCLE, cycleSyncId)!!.syncState,
        )
    }

    @Test
    fun `a dead session is not retried, and costs no local data`() = runTest {
        val cycleSyncId = markCyclePending()
        gateway.failures[cycleSyncId] = GriffGymError.Unauthorized()

        val result = runWorker()

        assertEquals(ListenableWorker.Result.failure(), result)

        // The app asks the lifter to sign in again; it does not delete a single row on the way.
        assertEquals(1, database.cloudSyncDao().allCycles().size)
        assertEquals(18, database.cloudSyncDao().allWorkoutTemplates().size)
        assertTrue(database.cloudSyncDao().allPlannedSets().isNotEmpty())

        // And the record is still queued, so it goes up the moment they sign back in.
        assertEquals(
            SyncState.FAILED,
            database.syncMetadataDao().get(SyncEntityType.TRAINING_CYCLE, cycleSyncId)!!.syncState,
        )
    }

    @Test
    fun `running twice sends the same record twice, not two records`() = runTest {
        val cycleSyncId = markCyclePending()

        runWorker()
        // WorkManager will happily run this again after a process death mid-pass, so the second
        // run must be harmless. Nothing is outstanding, so nothing is re-sent.
        runWorker()

        assertEquals(1, gateway.uploadedCycleIds.count { it == cycleSyncId })
        assertEquals(0, database.syncMetadataDao().countOutstanding())
    }

    // -----------------------------------------------------------------------------------------

    private suspend fun markCyclePending(): String {
        val syncId = database.trainingCycleDao().getCurrent()!!.syncId
        engine.markPending(SyncEntityType.TRAINING_CYCLE, syncId)
        return syncId
    }

    private suspend fun runWorker(): ListenableWorker.Result {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val worker = TestListenableWorkerBuilder<GriffGymSyncWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = GriffGymSyncWorker(
                        appContext,
                        workerParameters,
                        engine,
                        userMode,
                    )
                },
            )
            .build()

        return worker.doWork()
    }
}

private class FakeUserModeRepository(var mode: UserMode) : UserModeRepository {
    override fun observeUserMode(): Flow<UserMode> = MutableStateFlow(mode)
    override suspend fun getUserMode(): UserMode = mode
    override suspend fun chooseLocalOnly() { mode = UserMode.LocalOnly }
    override suspend fun markAuthenticated(session: AuthSession) {
        mode = UserMode.Authenticated(session.userId, session.email)
    }
    override suspend fun clearAccount() { mode = UserMode.Undecided }
}
