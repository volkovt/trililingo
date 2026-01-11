package com.trililingo.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "study_sessions",
    indices = [Index("language"), Index("activityType")]
)
data class StudySessionEntity(
    @PrimaryKey val sessionId: String = UUID.randomUUID().toString(),
    val language: String,
    val activityType: String, // "COMIC_COMPARE"
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val xpGained: Int,
    val avgResponseMs: Long,
    val correctCount: Int,
    val wrongCount: Int,
    val abandoned: Boolean
)
