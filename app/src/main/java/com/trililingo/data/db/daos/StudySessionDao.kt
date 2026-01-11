package com.trililingo.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trililingo.data.db.entities.StudySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: StudySessionEntity)

    @Query("UPDATE study_sessions SET endedAtMs = :endedAtMs, xpGained = :xp, avgResponseMs = :avg, correctCount = :correct, wrongCount = :wrong, abandoned = :abandoned WHERE sessionId = :sessionId")
    suspend fun finish(
        sessionId: String,
        endedAtMs: Long,
        xp: Int,
        avg: Long,
        correct: Int,
        wrong: Int,
        abandoned: Boolean
    )

    @Query("SELECT * FROM study_sessions ORDER BY startedAtMs DESC LIMIT 20")
    fun latestSessions(): Flow<List<StudySessionEntity>>
}
