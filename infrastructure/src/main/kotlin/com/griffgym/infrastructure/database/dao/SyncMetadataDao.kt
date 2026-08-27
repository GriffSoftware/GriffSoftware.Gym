package com.griffgym.infrastructure.database.dao

import androidx.room.Dao
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.griffgym.infrastructure.database.entity.SyncEntityType
import com.griffgym.infrastructure.database.entity.SyncMetadataEntity
import com.griffgym.infrastructure.database.entity.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE entityType = :type AND entityId = :id")
    suspend fun get(type: SyncEntityType, id: String): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata WHERE syncState != 'SYNCED'")
    suspend fun getOutstanding(): List<SyncMetadataEntity>

    @Query("SELECT COUNT(*) FROM sync_metadata WHERE syncState != 'SYNCED'")
    fun observeOutstandingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_metadata WHERE syncState != 'SYNCED'")
    suspend fun countOutstanding(): Int

    @Query("SELECT COUNT(*) FROM sync_metadata WHERE syncState = 'CONFLICT'")
    fun observeConflictCount(): Flow<Int>

    /**
     * The moment the last write was actually accepted, across everything.
     *
     * `MAX` over accepted timestamps only, so "last backup" cannot creep forward because a
     * sync started — a lifter reading that line is asking when their data was last safe.
     */
    @Query("SELECT MAX(lastSyncedAtUtc) FROM sync_metadata")
    fun observeLastSyncedAtUtc(): Flow<Long?>

    @Upsert
    suspend fun upsert(metadata: SyncMetadataEntity)

    @Upsert
    suspend fun upsertAll(metadata: List<SyncMetadataEntity>)

    @Query("DELETE FROM sync_metadata WHERE entityType = :type AND entityId = :id")
    suspend fun delete(type: SyncEntityType, id: String)

    @Query("DELETE FROM sync_metadata")
    suspend fun clear()

    /**
     * Marks everything as already on the server.
     *
     * Used at the end of a full restore: the local database was just built *from* the
     * server's copy, so re-uploading all of it would be pure waste.
     */
    @Query("UPDATE sync_metadata SET syncState = :state, failureMessage = NULL")
    suspend fun markAll(state: SyncState = SyncState.SYNCED)
}
