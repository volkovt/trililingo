package com.trililingo.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trililingo.data.db.entities.ActivityAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityAttemptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attempt: ActivityAttemptEntity)

    @Query("SELECT COUNT(*) FROM activity_attempts WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: String): Int

    @Query(
        """
        SELECT * FROM activity_attempts
        WHERE sessionId = :sessionId
        ORDER BY createdAtMs ASC
        """
    )
    fun bySession(sessionId: String): Flow<List<ActivityAttemptEntity>>
}
