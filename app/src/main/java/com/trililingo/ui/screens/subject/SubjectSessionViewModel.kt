package com.trililingo.ui.screens.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trililingo.data.catalog.SubjectStudyQuestion
import com.trililingo.data.repo.SubjectProgressRepository
import com.trililingo.data.repo.SubjectStudyRepository
import com.trililingo.domain.engine.ActivityEngine
import com.trililingo.domain.subject.SubjectStudyEngine
import com.trililingo.domain.subject.SubjectStudyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.random.Random
import javax.inject.Inject

enum class SubjectQuestionPhase {
    ANSWERING,
    REVEAL
}

data class SubjectQuestionChallenge(
    val questionId: String,
    val prompt: String,
    val expected: String,
    val options: List<String>,
    val correctIndex: Int,
    val difficulty: Int,
    val typeTags: List<String>,
    val tags: List<String>
)

data class SubjectRevealUi(
    val chosenIndex: Int,
    val correctIndex: Int,
    val isCorrect: Boolean,
    val xpAwarded: Int
)

data class SubjectSessionState(
    val loading: Boolean = true,
    val error: String? = null,

    val sessionId: String? = null,

    val challenges: List<SubjectQuestionChallenge> = emptyList(),
    val index: Int = 0,

    val totalXp: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,

    val sumResponseMs: Long = 0L,
    val attempts: Int = 0,

    val phase: SubjectQuestionPhase = SubjectQuestionPhase.ANSWERING,
    val reveal: SubjectRevealUi? = null
)

@HiltViewModel
class SubjectSessionViewModel @Inject constructor(
    private val studyRepo: SubjectStudyRepository,
    private val progressRepo: SubjectProgressRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SubjectSessionState())
    val state: StateFlow<SubjectSessionState> = _state

    private var submitInFlight = false
    private var startedKey: String? = null

    fun start(
        mode: String,
        subjectId: String,
        trackId: String,
        chapterId: String
    ) {
        val key = "${mode.trim().lowercase()}|$subjectId|$trackId|$chapterId"
        if (startedKey == key && !_state.value.loading) return
        startedKey = key

        viewModelScope.launch {
            _state.value = SubjectSessionState(loading = true)

            runCatching {
                val all = studyRepo.getQuestions(subjectId, trackId, chapterId)
                val valid = all.filter { !it.expected.isNullOrEmpty() && it.expected.any { exp -> exp.isNotBlank() } }
                if (valid.isEmpty()) {
                    return@runCatching SubjectSessionState(
                        loading = false,
                        error = "Sem perguntas válidas (campo expected vazio)."
                    )
                }

                val session = progressRepo.startSession(mode, subjectId, trackId, chapterId)

                val studyMode = if (mode.equals("daily", true)) SubjectStudyMode.DAILY else SubjectStudyMode.FREE

                val dayEpoch = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
                val seedKey = "$subjectId|$trackId|$chapterId"

                val picked = SubjectStudyEngine.buildSession(
                    mode = studyMode,
                    all = valid,
                    dayEpoch = dayEpoch,
                    seedKey = seedKey,
                    dailyLimit = 10
                )

                val allExpected = valid.flatMap { it.expected!! }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

                val challenges = picked.map { q ->
                    buildChallenge(
                        q = q,
                        allExpected = allExpected,
                        seedKey = "$seedKey|${q.id}|$dayEpoch|$mode"
                    )
                }

                SubjectSessionState(
                    loading = false,
                    error = null,
                    sessionId = session.sessionId,
                    challenges = challenges,
                    index = 0,
                    totalXp = 0,
                    correct = 0,
                    wrong = 0,
                    sumResponseMs = 0L,
                    attempts = 0,
                    phase = SubjectQuestionPhase.ANSWERING,
                    reveal = null
                )
            }.onSuccess { s ->
                _state.value = s
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
        onDone: (xp: Int, correct: Int, wrong: Int) -> Unit
    ) {
        val s = _state.value
        val sessionId = s.sessionId ?: return
        val ch = s.challenges.getOrNull(s.index) ?: return
        if (s.phase != SubjectQuestionPhase.ANSWERING) return
        if (submitInFlight) return

        submitInFlight = true

        viewModelScope.launch {
            try {
                val safeChosenIndex = chosenIndex.coerceIn(0, max(0, ch.options.size - 1))
                val ok = safeChosenIndex == ch.correctIndex

                val baseXp = ActivityEngine.score(ok, responseMs)
                val diffBonus = if (ok) ((ch.difficulty.coerceIn(1, 5) - 1) * 2) else 0
                val xpAwarded = baseXp + diffBonus

                progressRepo.recordAttempt(
                    sessionId = sessionId,
                    questionId = ch.questionId,
                    isCorrect = ok,
                    responseMs = responseMs,
                    chosen = ch.options.getOrNull(safeChosenIndex).orEmpty(),
                    correct = ch.expected,
                    baseXp = baseXp,
                    hintCount = 0,
                    xpMultiplier = 1.0,
                    xpAwarded = xpAwarded
                )

                val newCorrect = s.correct + if (ok) 1 else 0
                val newWrong = s.wrong + if (!ok) 1 else 0
                val newTotalXp = s.totalXp + xpAwarded

                val newSum = s.sumResponseMs + responseMs
                val newAttempts = s.attempts + 1

                _state.value = s.copy(
                    totalXp = newTotalXp,
                    correct = newCorrect,
                    wrong = newWrong,
                    sumResponseMs = newSum,
                    attempts = newAttempts,
                    phase = SubjectQuestionPhase.REVEAL,
                    reveal = SubjectRevealUi(
                        chosenIndex = safeChosenIndex,
                        correctIndex = ch.correctIndex,
                        isCorrect = ok,
                        xpAwarded = xpAwarded
                    )
                )

                onDone(newTotalXp, newCorrect, newWrong)
            } finally {
                submitInFlight = false
            }
        }
    }

    fun continueAfterReveal(onDone: (xp: Int, correct: Int, wrong: Int) -> Unit) {
        val s = _state.value
        val sessionId = s.sessionId ?: return
        if (s.phase != SubjectQuestionPhase.REVEAL) return

        viewModelScope.launch {
            val nextIndex = s.index + 1
            val isLast = nextIndex >= s.challenges.size

            val avg = if (s.attempts <= 0) 0L else (s.sumResponseMs / max(1, s.attempts))

            if (isLast) {
                progressRepo.finishSession(
                    sessionId = sessionId,
                    totalXp = s.totalXp,
                    avgResponseMs = avg,
                    correct = s.correct,
                    wrong = s.wrong,
                    abandoned = false
                )
                onDone(s.totalXp, s.correct, s.wrong)
            } else {
                _state.value = s.copy(
                    index = nextIndex,
                    phase = SubjectQuestionPhase.ANSWERING,
                    reveal = null
                )
            }
        }
    }

    fun abortSession(onAborted: () -> Unit) {
        val s = _state.value
        val sessionId = s.sessionId

        viewModelScope.launch {
            if (!sessionId.isNullOrBlank()) {
                val avg = if (s.attempts <= 0) 0L else (s.sumResponseMs / max(1, s.attempts))
                runCatching {
                    progressRepo.abortSession(
                        sessionId = sessionId,
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

    private fun buildChallenge(
        q: SubjectStudyQuestion,
        allExpected: List<String>,
        seedKey: String
    ): SubjectQuestionChallenge {
        val expected = q.expected!!.first { it.isNotBlank() }.trim()
        val diff = q.difficulty.coerceIn(1, 5)

        val pool = allExpected.filter { it != expected }.distinct()
        val rng = Random(stableSeed(seedKey))

        val distractors = pool.shuffled(rng).take(3)
        val opts = (distractors + expected).shuffled(rng)

        val correctIndex = opts.indexOf(expected).coerceAtLeast(0)

        return SubjectQuestionChallenge(
            questionId = q.id,
            prompt = q.prompt,
            expected = expected,
            options = opts,
            correctIndex = correctIndex,
            difficulty = diff,
            typeTags = q.typeTags,
            tags = q.tags
        )
    }

    private fun stableSeed(key: String): Int {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))

        var x = 0
        for (i in 0 until 4) {
            x = (x shl 8) or (bytes[i].toInt() and 0xff)
        }
        return x
    }
}
