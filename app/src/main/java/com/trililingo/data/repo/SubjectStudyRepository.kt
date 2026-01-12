package com.trililingo.data.repo

import android.content.Context
import com.trililingo.data.catalog.SubjectPack
import com.trililingo.data.catalog.SubjectStudyChapter
import com.trililingo.data.catalog.SubjectStudyContent
import com.trililingo.data.catalog.SubjectStudyQuestion
import com.trililingo.data.catalog.SubjectTrack
import com.trililingo.data.catalog.SubjectPacksLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubjectStudyRepository @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val mutex = Mutex()
    private var cachedPacks: List<SubjectPack>? = null

    private suspend fun packs(): List<SubjectPack> = mutex.withLock {
        if (cachedPacks != null) return@withLock cachedPacks!!
        val loader = SubjectPacksLoader(ctx)
        val loaded = loader.load().packs
        cachedPacks = loaded
        loaded
    }

    suspend fun getTrack(subjectId: String, trackId: String): SubjectTrack? = withContext(Dispatchers.IO) {
        val normalizedTrackId = normalizeTrackId(trackId)
        val p = packs().firstOrNull { (it.subjectId ?: "") == subjectId } ?: return@withContext null
        p.tracks.firstOrNull { it.id == normalizedTrackId }
    }

    suspend fun getStudyContent(subjectId: String, trackId: String): SubjectStudyContent? = withContext(Dispatchers.IO) {
        getTrack(subjectId, trackId)?.studyContent
    }

    suspend fun getChapters(subjectId: String, trackId: String): List<SubjectStudyChapter> = withContext(Dispatchers.IO) {
        val content = getStudyContent(subjectId, trackId) ?: return@withContext emptyList()
        content.chapters
            // evita exibir capítulos com questões vazias ou mal-formadas
            .filter { ch -> ch.questions.any { q -> q.prompt.isNotBlank() && q.expected.isNotEmpty() } }
    }

    suspend fun getQuestions(
        subjectId: String,
        trackId: String,
        chapterId: String?
    ): List<SubjectStudyQuestion> = withContext(Dispatchers.IO) {
        val chapters = getChapters(subjectId, trackId)
        val chosen = if (chapterId.isNullOrBlank() || chapterId == "all") chapters else chapters.filter { it.id == chapterId }
        chosen.flatMap { it.questions }
    }

    /**
     * Em alguns pontos da UI o Track ID pode vir no formato "topic::subjectId::trackId".
     * Aqui normalizamos para sempre usar o ID real do pack (ex.: "core").
     */
    private fun normalizeTrackId(trackId: String): String {
        val prefix = "topic::"
        if (!trackId.startsWith(prefix)) return trackId

        val raw = trackId.removePrefix(prefix)
        val parts = raw.split("::")
        return parts.getOrNull(1) ?: trackId
    }
}
