package com.trililingo.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trililingo.data.db.entities.SrsStateEntity

@Dao
interface SrsStateDao {

    @Query("SELECT * FROM srs_states WHERE itemId = :itemId LIMIT 1")
    suspend fun get(itemId: String): SrsStateEntity?

    @Query("SELECT * FROM srs_states WHERE itemId IN (:itemIds)")
    suspend fun getAll(itemIds: List<String>): List<SrsStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SrsStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<SrsStateEntity>)

    @Query("SELECT itemId FROM srs_states WHERE itemId IN (:ids)")
    suspend fun getExistingItemIds(ids: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<SrsStateEntity>)
}
