package com.griffgym.infrastructure.di

import com.griffgym.domain.repository.CloudBackupRepository
import com.griffgym.domain.repository.CloudSyncStatusRepository
import com.griffgym.domain.repository.LocalTrainingDataRepository
import com.griffgym.infrastructure.repository.CloudBackupRepositoryImpl
import com.griffgym.infrastructure.repository.RoomLocalTrainingDataRepository
import com.griffgym.infrastructure.sync.CloudStateGateway
import com.griffgym.infrastructure.sync.EngineSyncRecorder
import com.griffgym.infrastructure.sync.RetrofitCloudStateGateway
import com.griffgym.infrastructure.sync.SyncRecorder
import com.griffgym.infrastructure.sync.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The cloud half of infrastructure, kept in its own module.
 *
 * Separate from `RepositoryModule` because it is separable: everything bound here exists to
 * back a lifter's data up, and none of it is on the path between a lifter and their training.
 * That the app would still work if this module were removed is a property worth being able to
 * see at a glance.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SyncModule {

    @Binds
    abstract fun bindCloudStateGateway(impl: RetrofitCloudStateGateway): CloudStateGateway

    @Binds
    abstract fun bindSyncRecorder(impl: EngineSyncRecorder): SyncRecorder

    @Binds
    abstract fun bindCloudBackupRepository(impl: CloudBackupRepositoryImpl): CloudBackupRepository

    /**
     * The scheduler is the status repository. Whether data is backed up and when the next
     * attempt happens are the same question asked twice, and splitting them would mean two
     * things that could disagree about it.
     */
    @Binds
    abstract fun bindCloudSyncStatusRepository(
        impl: WorkManagerSyncScheduler,
    ): CloudSyncStatusRepository

    @Binds
    abstract fun bindLocalTrainingDataRepository(
        impl: RoomLocalTrainingDataRepository,
    ): LocalTrainingDataRepository
}
