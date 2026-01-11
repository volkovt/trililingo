package com.trililingo.di

import android.content.Context
import com.trililingo.core.time.TimeProvider
import com.trililingo.data.db.TrililingoDb
import com.trililingo.data.repo.SubjectContentRepository
import com.trililingo.data.repo.SubjectProgressRepository
import com.trililingo.data.repo.SubjectStudyRepository
import com.trililingo.data.repo.UserPrefsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SubjectModule {

    @Provides
    @Singleton
    fun provideSubjectContentRepository(
        @ApplicationContext ctx: Context
    ): SubjectContentRepository = SubjectContentRepository(ctx)

    @Provides
    @Singleton
    fun provideSubjectStudyRepository(
        @ApplicationContext ctx: Context
    ): SubjectStudyRepository = SubjectStudyRepository(ctx)

    @Provides
    @Singleton
    fun provideSubjectProgressRepository(
        db: TrililingoDb,
        prefs: UserPrefsRepository,
        time: TimeProvider
    ): SubjectProgressRepository = SubjectProgressRepository(db, prefs, time)
}
