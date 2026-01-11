package com.trililingo.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.room.Room
import com.trililingo.core.time.TimeProvider
import com.trililingo.data.datastore.UserPrefsDataStore
import com.trililingo.data.db.Migrations
import com.trililingo.data.db.TrililingoDb
import com.trililingo.data.repo.ContentSeeder
import com.trililingo.data.repo.StudyRepository
import com.trililingo.data.repo.UserPrefsRepository
import com.trililingo.python.PythonSrsBridge
import com.trililingo.sync.SyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.userPrefsStore: DataStore<com.trililingo.datastore.UserPrefs> by dataStore(
    fileName = "user_prefs.pb",
    serializer = UserPrefsDataStore.SerializerImpl
)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideTimeProvider(): TimeProvider = TimeProvider.System

    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): TrililingoDb =
        Room.databaseBuilder(ctx, TrililingoDb::class.java, "trililingo.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addMigrations(Migrations.MIGRATION_1_2)
            .build()

    @Provides @Singleton
    fun provideUserPrefsStore(@ApplicationContext ctx: Context): DataStore<com.trililingo.datastore.UserPrefs> =
        ctx.userPrefsStore

    @Provides @Singleton
    fun provideUserPrefsRepo(
        store: DataStore<com.trililingo.datastore.UserPrefs>,
        timeProvider: TimeProvider
    ): UserPrefsRepository = UserPrefsRepository(store, timeProvider)

    @Provides @Singleton
    fun providePythonBridge(): PythonSrsBridge = PythonSrsBridge()

    @Provides @Singleton
    fun provideStudyRepo(
        db: TrililingoDb,
        userPrefsRepository: UserPrefsRepository,
        pythonSrsBridge: PythonSrsBridge,
        timeProvider: TimeProvider,
        seeder: ContentSeeder
    ): StudyRepository = StudyRepository(
        db = db,
        prefs = userPrefsRepository,
        srs = pythonSrsBridge,
        time = timeProvider,
        seeder = seeder
    )

    @Provides @Singleton
    fun provideContentSeeder(
        @ApplicationContext ctx: Context,
        db: TrililingoDb
    ): ContentSeeder = ContentSeeder(ctx, db)

    @Provides @Singleton
    fun provideSyncScheduler(@ApplicationContext ctx: Context): SyncScheduler =
        SyncScheduler(ctx)
}
