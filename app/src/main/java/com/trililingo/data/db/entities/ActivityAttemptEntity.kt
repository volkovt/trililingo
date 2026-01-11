package com.trililingo.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "activity_attempts",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["itemId"]),
        Index(value = ["createdAtMs"])
    ]
)
data class ActivityAttemptEntity(
    @PrimaryKey val attemptId: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val itemId: String,
    val isCorrect: Boolean,
    val responseMs: Long,
    val chosenAnswer: String,
    val correctAnswer: String,
    val createdAtMs: Long,
    @ColumnInfo(defaultValue = "0")
    val hintCount: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val baseXp: Int = 0,
    @ColumnInfo(defaultValue = "1.0")
    val xpMultiplier: Double = 1.0,
    @ColumnInfo(defaultValue = "0")
    val xpAwarded: Int = 0
)
