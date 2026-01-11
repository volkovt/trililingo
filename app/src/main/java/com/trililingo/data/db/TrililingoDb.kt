package com.trililingo.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.trililingo.data.db.daos.*
import com.trililingo.data.db.entities.*

@Database(
    entities = [
        LanguageItemEntity::class,
        SrsStateEntity::class,
        StudySessionEntity::class,
        ActivityAttemptEntity::class,
        SyncEventEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class TrililingoDb : RoomDatabase() {
    abstract fun languageItemDao(): LanguageItemDao
    abstract fun srsStateDao(): SrsStateDao
    abstract fun sessionDao(): StudySessionDao
    abstract fun attemptDao(): ActivityAttemptDao
    abstract fun syncEventDao(): SyncEventDao
}
