package com.trililingo.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "sync_events",
    indices = [Index("state"), Index("type")]
)
data class SyncEventEntity(
    @PrimaryKey val eventId: String = UUID.randomUUID().toString(),
    val type: String,       // "SESSION_END", "ATTEMPT", etc.
    val payloadJson: String,
    val createdAtMs: Long,
    val state: String       // "PENDING" | "SENT" | "FAILED"
)
