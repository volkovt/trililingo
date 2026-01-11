package com.trililingo.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "srs_states")
data class SrsStateEntity(
    @PrimaryKey val itemId: String,
    val ease: Double,
    val intervalDays: Int,
    val repetitions: Int,
    val lapses: Int,
    val dueAtMs: Long
)
