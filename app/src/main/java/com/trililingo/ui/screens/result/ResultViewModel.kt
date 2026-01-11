package com.trililingo.ui.screens.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trililingo.core.language.PronunciationPtResolver
import com.trililingo.data.repo.StudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val repo: StudyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ResultUiState())
    val state: StateFlow<ResultUiState> = _state

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

                    val ui = attempts.mapNotNull { a ->
                        val item = itemsById[a.itemId] ?: return@mapNotNull null
                        val meta = metaById[a.itemId]

                        val pronunciationPt = PronunciationPtResolver.resolve(
                            languageCode = item.language,
                            itemPronunciationPt = meta?.pronunciationPt,
                            romanization = meta?.romanization?.value,
                            fallbackText = item.answer
                        )

                        ResultAttemptUi(
                            itemId = a.itemId,
                            answer = item.answer,
                            meaning = item.meaning,
                            pronunciationPt = pronunciationPt,
                            romanization = meta?.romanization?.value,

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
}
