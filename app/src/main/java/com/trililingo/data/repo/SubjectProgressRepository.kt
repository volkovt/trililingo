package com.trililingo.data.repo

import com.trililingo.core.time.TimeProvider
import com.trililingo.data.db.TrililingoDb
import com.trililingo.data.db.entities.ActivityAttemptEntity
import com.trililingo.data.db.entities.StudySessionEntity
import com.trililingo.data.db.entities.SyncEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persistência de progresso do fluxo "Subjects" usando as mesmas tabelas:
 * - study_sessions (sessão)
 * - activity_attempts (tentativas)
 * - sync_events (opcional, mas mantém padrão offline-first)
 */
class SubjectProgressRepository(
    private val db: TrililingoDb,
    private val prefs: UserPrefsRepository,
    private val time: TimeProvider
) {
    companion object {
        const val LANGUAGE_SUBJECT = "SUBJECT"
        private const val ITEM_PREFIX = "SUBQ:"
    }

    fun buildActivityType(
        mode: String,
        subjectId: String,
        trackId: String,
        chapterId: String
    ): String {
        val m = mode.uppercase()
        return "SUBJECT_${m}|$subjectId|$trackId|$chapterId"
    }

    private fun buildItemId(questionId: String): String = "$ITEM_PREFIX$questionId"

    suspend fun startSession(
        mode: String,
        subjectId: String,
        trackId: String,
        chapterId: String
    ): StudySessionEntity = withContext(Dispatchers.IO) {
        val now = time.nowMs()

        val session = StudySessionEntity(
            language = LANGUAGE_SUBJECT,
            activityType = buildActivityType(mode, subjectId, trackId, chapterId),
            startedAtMs = now,
            endedAtMs = null,
            xpGained = 0,
            avgResponseMs = 0,
            correctCount = 0,
            wrongCount = 0,
            abandoned = false
        )

        db.sessionDao().insert(session)

        db.syncEventDao().insert(
            SyncEventEntity(
                type = "SUBJECT_SESSION_START",
                payloadJson = """
                    {
                      "sessionId":"${session.sessionId}",
                      "activityType":"${session.activityType}",
                      "startedAtMs":$now
                    }
                """.trimIndent(),
                createdAtMs = now,
                state = "PENDING"
            )
        )

        session
    }

    suspend fun recordAttempt(
        sessionId: String,
        questionId: String,
        isCorrect: Boolean,
        responseMs: Long,
        chosen: String,
        correct: String,
        baseXp: Int,
        hintCount: Int,
        xpMultiplier: Double,
        xpAwarded: Int
    ) = withContext(Dispatchers.IO) {
        val now = time.nowMs()
        val itemId = buildItemId(questionId)

        db.attemptDao().insert(
            ActivityAttemptEntity(
                sessionId = sessionId,
                itemId = itemId,
                isCorrect = isCorrect,
                responseMs = responseMs,
                chosenAnswer = chosen,
                correctAnswer = correct,
                hintCount = hintCount,
                baseXp = baseXp,
                xpMultiplier = xpMultiplier,
                xpAwarded = xpAwarded,
                createdAtMs = now
            )
        )

        db.syncEventDao().insert(
            SyncEventEntity(
                type = "SUBJECT_ATTEMPT",
                payloadJson = """
                    {
                      "sessionId":"$sessionId",
                      "questionId":"$questionId",
                      "itemId":"$itemId",
                      "correct":$isCorrect,
                      "responseMs":$responseMs,
                      "baseXp":$baseXp,
                      "hintCount":$hintCount,
                      "xpMultiplier":$xpMultiplier,
                      "xp":$xpAwarded
                    }
                """.trimIndent(),
                createdAtMs = now,
                state = "PENDING"
            )
        )
    }

    suspend fun finishSession(
        sessionId: String,
        totalXp: Int,
        avgResponseMs: Long,
        correct: Int,
        wrong: Int,
        abandoned: Boolean
    ) = withContext(Dispatchers.IO) {
        val end = time.nowMs()

        db.sessionDao().finish(
            sessionId = sessionId,
            endedAtMs = end,
            xp = totalXp,
            avg = avgResponseMs,
            correct = correct,
            wrong = wrong,
            abandoned = abandoned
        )

        prefs.addXpAndUpdateStreak(totalXp)

        db.syncEventDao().insert(
            SyncEventEntity(
                type = "SUBJECT_SESSION_END",
                payloadJson = """{"sessionId":"$sessionId","xp":$totalXp,"endedAtMs":$end}""",
                createdAtMs = end,
                state = "PENDING"
            )
        )
    }

    suspend fun abortSession(
        sessionId: String,
        totalXp: Int,
        avgResponseMs: Long,
        correct: Int,
        wrong: Int
    ) = withContext(Dispatchers.IO) {
        val end = time.nowMs()

        db.sessionDao().finish(
            sessionId = sessionId,
            endedAtMs = end,
            xp = totalXp,
            avg = avgResponseMs,
            correct = correct,
            wrong = wrong,
            abandoned = true
        )

        db.syncEventDao().insert(
            SyncEventEntity(
                type = "SUBJECT_SESSION_ABORT",
                payloadJson = """{"sessionId":"$sessionId","xp":$totalXp,"endedAtMs":$end}""",
                createdAtMs = end,
                state = "PENDING"
            )
        )
    }
}
