package com.trililingo.ui.screens.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trililingo.core.time.TimeProvider
import com.trililingo.data.catalog.SubjectStudyQuestion
import com.trililingo.data.repo.SubjectProgressRepository
import com.trililingo.data.repo.SubjectStudyRepository
import com.trililingo.domain.subject.SubjectStudyEngine
import com.trililingo.domain.subject.SubjectStudyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SubjectQuestionPhase {
    ANSWERING,
    REVEAL
}

data class SubjectReveal(
    val chosenIndex: Int,
    val correctIndex: Int,
    val isCorrect: Boolean,
    val xpAwarded: Int
)

data class SubjectMcqChallenge(
    val questionId: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int
)

data class SubjectSessionState(
    val loading: Boolean = true,
    val error: String? = null,

    // persistência
    val sessionId: String? = null,

    val challenges: List<SubjectMcqChallenge> = emptyList(),
    val index: Int = 0,

    val phase: SubjectQuestionPhase = SubjectQuestionPhase.ANSWERING,
    val reveal: SubjectReveal? = null,

    val totalXp: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,

    // métricas para fechar a sessão
    val answeredCount: Int = 0,
    val sumResponseMs: Long = 0L
)

@HiltViewModel
class SubjectSessionViewModel @Inject constructor(
    private val repo: SubjectStudyRepository,
    private val progress: SubjectProgressRepository,
    private val time: TimeProvider
) : ViewModel() {

    private val _state = MutableStateFlow(SubjectSessionState())
    val state: StateFlow<SubjectSessionState> = _state

    fun start(
        mode: String,
        subjectId: String,
        trackId: String,
        chapterId: String
    ) {
        viewModelScope.launch {
            _state.value = SubjectSessionState(loading = true)

            runCatching {
                val allQuestions = repo.getQuestions(subjectId, trackId, chapterId)
                    .filter { !it.expected.isNullOrBlank() }

                val m = if (mode.equals("daily", true)) SubjectStudyMode.DAILY else SubjectStudyMode.FREE

                val sessionQuestions = SubjectStudyEngine.buildSession(
                    mode = m,
                    all = allQuestions,
                    dayEpoch = time.todayEpochDay(),
                    seedKey = "$subjectId|$trackId|$chapterId",
                    dailyLimit = 10
                )

                val challenges = buildMcqChallenges(sessionQuestions, allQuestions)
                if (challenges.isEmpty()) {
                    error("Sem perguntas válidas (expected vazio) para este capítulo.")
                }

                val session = progress.startSession(mode, subjectId, trackId, chapterId)
                session.sessionId to challenges
            }.onSuccess { (sessionId, challenges) ->
                _state.value = SubjectSessionState(
                    loading = false,
                    sessionId = sessionId,
                    challenges = challenges
                )
            }.onFailure { e ->
                _state.value = SubjectSessionState(
                    loading = false,
                    error = e.message ?: "Erro ao montar sessão"
                )
            }
        }
    }

    fun submitAnswer(
        chosenIndex: Int,
        responseMs: Long,
        onFinished: (xp: Int, c: Int, w: Int) -> Unit // mantido p/ compatibilidade com a Screen (mesmo que finalize no continueAfterReveal)
    ) {
        val s = _state.value
        if (s.loading) return
        if (s.phase != SubjectQuestionPhase.ANSWERING) return

        val challenge = s.challenges.getOrNull(s.index) ?: return
        val isCorrect = chosenIndex == challenge.correctIndex

        // scoring atual (mantive tua regra)
        val xpAward = if (isCorrect) 10 else 2

        val newXp = s.totalXp + xpAward
        val newCorrect = s.correct + if (isCorrect) 1 else 0
        val newWrong = s.wrong + if (!isCorrect) 1 else 0

        val chosen = challenge.options.getOrNull(chosenIndex).orEmpty()
        val correct = challenge.options.getOrNull(challenge.correctIndex).orEmpty()

        // atualiza UI imediato
        _state.value = s.copy(
            phase = SubjectQuestionPhase.REVEAL,
            reveal = SubjectReveal(
                chosenIndex = chosenIndex,
                correctIndex = challenge.correctIndex,
                isCorrect = isCorrect,
                xpAwarded = xpAward
            ),
            totalXp = newXp,
            correct = newCorrect,
            wrong = newWrong,
            answeredCount = s.answeredCount + 1,
            sumResponseMs = s.sumResponseMs + responseMs
        )

        // persiste tentativa (fire-and-forget)
        val sid = s.sessionId
        if (!sid.isNullOrBlank()) {
            viewModelScope.launch {
                runCatching {
                    progress.recordAttempt(
                        sessionId = sid,
                        questionId = challenge.questionId,
                        isCorrect = isCorrect,
                        responseMs = responseMs,
                        chosen = chosen,
                        correct = correct,
                        xpAwarded = xpAward
                    )
                }
            }
        }
    }

    fun continueAfterReveal(onFinished: (xp: Int, c: Int, w: Int) -> Unit) {
        val s = _state.value
        if (s.phase != SubjectQuestionPhase.REVEAL) return

        val isLast = (s.index + 1) >= s.challenges.size
        if (isLast) {
            val sid = s.sessionId

            viewModelScope.launch {
                if (!sid.isNullOrBlank()) {
                    val avg = if (s.answeredCount > 0) (s.sumResponseMs / s.answeredCount) else 0L

                    // sessão completa (abandoned=false)
                    runCatching {
                        progress.finishSession(
                            sessionId = sid,
                            totalXp = s.totalXp,
                            avgResponseMs = avg,
                            correct = s.correct,
                            wrong = s.wrong,
                            abandoned = false
                        )
                    }
                } else {
                    // fallback defensivo: se por algum motivo não abriu sessão
                    // (não deveria acontecer, mas evita perder XP/streak)
                    // se quiser, posso remover isso e tratar como erro.
                    runCatching {
                        // mesmo comportamento do fluxo antigo:
                        // progress.finishSession já chama prefs.addXpAndUpdateStreak,
                        // então aqui só faria sentido se sid == null.
                    }
                }

                onFinished(s.totalXp, s.correct, s.wrong)
            }
        } else {
            _state.value = s.copy(
                index = s.index + 1,
                phase = SubjectQuestionPhase.ANSWERING,
                reveal = null
            )
        }
    }

    fun abortSession(onAborted: () -> Unit) {
        val s = _state.value
        val sid = s.sessionId

        viewModelScope.launch {
            if (!sid.isNullOrBlank()) {
                val avg = if (s.answeredCount > 0) (s.sumResponseMs / s.answeredCount) else 0L

                runCatching {
                    progress.abortSession(
                        sessionId = sid,
                        totalXp = s.totalXp,
                        avgResponseMs = avg,
                        correct = s.correct,
                        wrong = s.wrong
                    )
                }
            }

            onAborted()
        }
    }

    private fun buildMcqChallenges(
        session: List<SubjectStudyQuestion>,
        pool: List<SubjectStudyQuestion>
    ): List<SubjectMcqChallenge> {
        val expectedPool = pool.mapNotNull { it.expected?.trim() }.filter { it.isNotBlank() }.distinct()

        return session.mapNotNull { q ->
            val correct = q.expected?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            val distractors = expectedPool
                .asSequence()
                .filter { it != correct }
                .shuffled()
                .take(3)
                .toList()

            val options = (distractors + correct).shuffled()
            val correctIndex = options.indexOf(correct).coerceAtLeast(0)

            SubjectMcqChallenge(
                questionId = q.id,
                prompt = q.prompt,
                options = options,
                correctIndex = correctIndex
            )
        }
    }

}
