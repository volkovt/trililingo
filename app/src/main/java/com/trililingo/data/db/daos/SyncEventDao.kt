package com.trililingo.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trililingo.data.db.entities.SyncEventEntity

@Dao
interface SyncEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SyncEventEntity)

    @Query("SELECT * FROM sync_events WHERE state = 'PENDING' ORDER BY createdAtMs ASC LIMIT :limit")
    suspend fun getPending(limit: Int): List<SyncEventEntity>

    @Query("UPDATE sync_events SET state = :newState WHERE eventId = :eventId")
    suspend fun setState(eventId: String, newState: String)
}
