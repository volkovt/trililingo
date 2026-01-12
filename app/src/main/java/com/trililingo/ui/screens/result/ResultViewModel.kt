package com.trililingo.ui.screens.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trililingo.core.language.PronunciationPtResolver
import com.trililingo.data.repo.StudyRepository
import com.trililingo.data.repo.SubjectStudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ResultAttemptUi(
    val itemId: String,
    val answer: String,
    val meaning: String?,
    val pronunciationPt: String?,
    val romanization: String?,

    val isCorrect: Boolean,
    val chosenAnswer: String,
    val correctAnswer: String,

    val responseMs: Long,
    val hintCount: Int,
    val baseXp: Int,
    val xpMultiplier: Double,
    val xpAwarded: Int
)

data class ResultUiState(
    val loading: Boolean = false,
    val sessionId: String? = null,
    val correctItems: List<ResultAttemptUi> = emptyList(),
    val wrongItems: List<ResultAttemptUi> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val repo: StudyRepository,
    private val subjectRepo: SubjectStudyRepository
) : ViewModel() {

    companion object {
        private const val SUBJECT_ITEM_PREFIX = "SUBQ:"
        private const val SUBJECT_ACTIVITY_PREFIX = "SUBJECT_"
    }

    private val _state = MutableStateFlow(ResultUiState())
    val state: StateFlow<ResultUiState> = _state

    private var cachedSubjectKey: String? = null
    private var cachedSubjectPromptByQuestionId: Map<String, String> = emptyMap()

    fun bindSession(sessionId: String?) {
        if (sessionId.isNullOrBlank()) {
            _state.value = ResultUiState(
                loading = false,
                sessionId = null,
                correctItems = emptyList(),
                wrongItems = emptyList(),
                error = null
            )
            return
        }

        if (_state.value.sessionId == sessionId) return

        cachedSubjectKey = null
        cachedSubjectPromptByQuestionId = emptyMap()

        _state.value = _state.value.copy(loading = true, sessionId = sessionId, error = null)

        viewModelScope.launch {
            try {
                repo.attemptsForSession(sessionId).collectLatest { attempts ->
                    val ids = attempts.map { it.itemId }.distinct()

                    val itemsById = withContext(Dispatchers.IO) {
                        repo.loadItemsByIds(ids).associateBy { it.id }
                    }

                    val metaById = withContext(Dispatchers.IO) {
                        repo.loadItemMeta(ids)
                    }

                    val hasSubjectAttempts = ids.any { it.startsWith(SUBJECT_ITEM_PREFIX) }
                    val promptByQuestionId = if (hasSubjectAttempts) {
                        ensureSubjectPromptCacheForSession(sessionId)
                        cachedSubjectPromptByQuestionId
                    } else emptyMap()

                    val ui = attempts.map { a ->
                        val item = itemsById[a.itemId] // null em Subjects
                        val meta = metaById[a.itemId]

                        val isSubject = a.itemId.startsWith(SUBJECT_ITEM_PREFIX)
                        val questionId = if (isSubject) a.itemId.removePrefix(SUBJECT_ITEM_PREFIX) else null
                        val subjectPrompt = if (questionId != null) promptByQuestionId[questionId] else null

                        val pronunciationPt = PronunciationPtResolver.resolve(
                            languageCode = item?.language ?: "SUBJECT",
                            itemPronunciationPt = meta?.pronunciationPt,
                            romanization = meta?.romanization?.value,
                            fallbackText = a.correctAnswer
                        )

                        ResultAttemptUi(
                            itemId = a.itemId,
                            answer = item?.answer ?: a.correctAnswer,
                            meaning = item?.meaning ?: subjectPrompt,
                            pronunciationPt = if (item != null) pronunciationPt else null,
                            romanization = if (item != null) meta?.romanization?.value else null,

                            isCorrect = a.isCorrect,
                            chosenAnswer = a.chosenAnswer,
                            correctAnswer = a.correctAnswer,

                            responseMs = a.responseMs,
                            hintCount = a.hintCount,
                            baseXp = a.baseXp,
                            xpMultiplier = a.xpMultiplier,
                            xpAwarded = a.xpAwarded
                        )
                    }

                    _state.value = ResultUiState(
                        loading = false,
                        sessionId = sessionId,
                        correctItems = ui.filter { it.isCorrect },
                        wrongItems = ui.filterNot { it.isCorrect },
                        error = null
                    )
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = t.message ?: "Erro ao carregar detalhes da sessão"
                )
            }
        }
    }

    private suspend fun ensureSubjectPromptCacheForSession(sessionId: String) {
        if (cachedSubjectKey == sessionId && cachedSubjectPromptByQuestionId.isNotEmpty()) return

        val session = withContext(Dispatchers.IO) {
            repo.latestSessions().first().firstOrNull { it.sessionId == sessionId }
        }

        val activityType = session?.activityType.orEmpty()
        if (!activityType.startsWith(SUBJECT_ACTIVITY_PREFIX)) {
            cachedSubjectKey = sessionId
            cachedSubjectPromptByQuestionId = emptyMap()
            return
        }

        val parts = activityType.split("|", limit = 4)
        if (parts.size < 4) {
            cachedSubjectKey = sessionId
            cachedSubjectPromptByQuestionId = emptyMap()
            return
        }

        val subjectId = parts[1]
        val trackId = parts[2]
        val chapterId = parts[3]

        val questions = withContext(Dispatchers.IO) {
            subjectRepo.getQuestions(subjectId, trackId, chapterId)
        }

        cachedSubjectKey = sessionId
        cachedSubjectPromptByQuestionId = questions
            .filter { it.id.isNotBlank() && it.prompt.isNotBlank() }
            .associate { it.id to it.prompt }
    }
}
