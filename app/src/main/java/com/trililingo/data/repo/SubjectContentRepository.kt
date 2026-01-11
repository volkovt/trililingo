package com.trililingo.data.repo

import android.content.Context
import com.trililingo.data.catalog.SubjectPack
import com.trililingo.data.catalog.SubjectPacksLoader
import com.trililingo.data.catalog.SubjectTrack
import com.trililingo.ui.catalog.TopicTrackIds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class TopicTrackContent(
    val subjectId: String,
    val subjectName: String,
    val track: SubjectTrack
)

@Singleton
class SubjectContentRepository @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val mutex = Mutex()
    private var cached: List<SubjectPack>? = null

    private suspend fun loadPacks(): List<SubjectPack> = mutex.withLock {
        cached?.let { return@withLock it }
        val packs = SubjectPacksLoader(ctx).load().packs
        cached = packs
        packs
    }

    suspend fun getTopicTrackContent(trackUiId: String): TopicTrackContent? = withContext(Dispatchers.IO) {
        val decoded = TopicTrackIds.decode(trackUiId) ?: return@withContext null
        val packs = loadPacks()

        val pack = packs.firstOrNull { (it.subjectId ?: "") == decoded.subjectId }
            ?: packs.firstOrNull { it.subjectName.lowercase().replace(" ", "_") == decoded.subjectId }
            ?: return@withContext null

        val track = pack.tracks.firstOrNull { it.id == decoded.trackId && it.enabled } ?: return@withContext null

        TopicTrackContent(
            subjectId = decoded.subjectId,
            subjectName = pack.subjectName,
            track = track
        )
    }
}
