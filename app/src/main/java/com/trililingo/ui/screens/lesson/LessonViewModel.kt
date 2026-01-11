package com.trililingo.ui.screens.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trililingo.data.repo.ItemMeta
import com.trililingo.data.repo.StudyRepository
import com.trililingo.domain.engine.ActivityDefinition
import com.trililingo.domain.engine.AnswerMode
import com.trililingo.domain.engine.Challenge
import com.trililingo.domain.engine.StudyDifficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.random.Random

enum class QuestionPhase { ANSWERING, REVEAL }

data class RevealUi(
    val chosen: String,
    val correct: String,
    val isCorrect: Boolean,
    val xpAwarded: Int,
    val hintCount: Int,
    val acceptanceNote: String? = null
)

data class LessonUiState(
    val loading: Boolean = true,
    val sessionId: String? = null,
    val challenges: List<Challenge> = emptyList(),
    val answerModes: List<AnswerMode> = emptyList(),

    val index: Int = 0,
    val totalXp: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    val lastWasCorrect: Boolean? = null,
    val lastXp: Int = 0,
    val metaById: Map<String, ItemMeta> = emptyMap(),
    val sumResponseMs: Long = 0,
    val attempts: Int = 0,
    val mode: String = "practice",
    val difficulty: StudyDifficulty = StudyDifficulty.BASIC,
    val error: String? = null,

    val phase: QuestionPhase = QuestionPhase.ANSWERING,
    val reveal: RevealUi? = null
)

@HiltViewModel
class LessonViewModel @Inject constructor(
    private val repo: StudyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LessonUiState())
    val state: StateFlow<LessonUiState> = _state
    private var submitInFlight = false

    fun start(language: String, skill: String, mode: String, difficulty: StudyDifficulty) {
        viewModelScope.launch {
            try {
                _state.value = LessonUiState(loading = true, mode = mode, difficulty = difficulty)

                val activityType = if (mode.equals("daily", ignoreCase = true)) {
                    "DAILY_COMIC_COMPARE"
                } else {
                    "COMIC_COMPARE"
                }

                val def = ActivityDefinition(
                    activityType = activityType,
                    language = language,
                    skill = skill,
                    length = if (mode.equals("daily", ignoreCase = true)) 10 else 7
                )

                val session = repo.startSession(def)
                val challenges = repo.loadChallenges(def, mode)
                val metaById = repo.loadItemMeta(challenges.map { it.itemId })

                val modes = buildAnswerModes(
                    sessionSeed = session.sessionId.hashCode(),
                    challenges = challenges,
                    difficulty = difficulty
                )

                _state.value = LessonUiState(
                    loading = false,
                    sessionId = session.sessionId,
                    challenges = challenges,
                    answerModes = modes,
                    index = 0,
                    metaById = metaById,
                    mode = mode,
                    difficulty = difficulty,
                    phase = QuestionPhase.ANSWERING,
                    reveal = null
                )
            } catch (t: Throwable) {
                _state.value = LessonUiState(
                    loading = false,
                    error = t.message,
                    mode = mode,
                    difficulty = difficulty
                )
            }
        }
    }

    private fun buildAnswerModes(
        sessionSeed: Int,
        challenges: List<Challenge>,
        difficulty: StudyDifficulty
    ): List<AnswerMode> {
        return when (difficulty) {
            StudyDifficulty.BASIC -> List(challenges.size) { AnswerMode.MULTIPLE_CHOICE }
            StudyDifficulty.HARDCORE -> List(challenges.size) { AnswerMode.TYPING }
            StudyDifficulty.MIXED -> {
                challenges.mapIndexed { idx, c ->
                    val seed = sessionSeed xor c.itemId.hashCode() xor idx
                    val r = Random(seed).nextInt(100)
                    if (r < 50) AnswerMode.TYPING else AnswerMode.MULTIPLE_CHOICE
                }.let { list ->
                    if (list.size <= 1) list else {
                        val hasTyping = list.any { it == AnswerMode.TYPING }
                        val hasChoice = list.any { it == AnswerMode.MULTIPLE_CHOICE }
                        when {
                            hasTyping && hasChoice -> list
                            !hasTyping -> list.toMutableList().also { it[0] = AnswerMode.TYPING }
                            !hasChoice -> list.toMutableList().also { it[0] = AnswerMode.MULTIPLE_CHOICE }
                            else -> list
                        }
                    }
                }
            }
        }
    }

    /**
     * Penalidade de dica:
     * - 0: 1.00
     * - 1: 0.35
     * - 2: 0.25
     * - 3: 0.18
     * - 4+: 0.12
     */
    private fun hintMultiplier(hintCount: Int): Double {
        return when (hintCount.coerceAtLeast(0)) {
            0 -> 1.00
            1 -> 0.35
            2 -> 0.25
            3 -> 0.18
            else -> 0.12
        }
    }

    // ==========================
    // ✅ Answer matching (GENÉRICO, agnóstico de idioma)
    // ==========================

    private data class AnswerEval(
        val isCorrect: Boolean,
        val acceptanceNote: String?
    )

    /**
     * Normalização genérica para tolerância:
     * - NFKC: unifica full-width/half-width e variações unicode comuns (útil p/ JP/CN)
     * - remove diacríticos (acentos) via NFD + strip \p{Mn}
     * - remove pontuação "decorativa" (· - _ ' etc.)
     * - colapsa espaços
     * - lowercase (Locale.ROOT)
     *
     * Não tenta “consertar” conteúdo (não faz aliases, não muda letras).
     */
    private fun normalizeForMatch(raw: String): String {
        if (raw.isBlank()) return ""

        // Unifica representações unicode
        var s = Normalizer.normalize(raw, Normalizer.Form.NFKC)

        // Normaliza espaços (inclui full-width space)
        s = s.replace('\u3000', ' ')

        // Remove pontuação "decorativa" que normalmente não deve contar como erro
        // (mantemos dígitos e letras; removemos separadores comuns)
        s = s.replace("·", " ")
            .replace("-", " ")
            .replace("_", " ")
            .replace("’", "'")
            .replace("'", "")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("\"", "")

        // Remove diacríticos (acentos/tons por marca)
        val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
        s = decomposed.replace(Regex("\\p{Mn}+"), "")

        // Trim + colapsa espaços
        s = s.trim()
            .split(Regex("\\s+"))
            .joinToString(" ")

        // Case-insensitive
        s = s.lowercase(Locale.ROOT)

        return s
    }

    private fun evalAnswer(chosenRaw: String, correctRaw: String): AnswerEval {
        val chosenTrim = chosenRaw.trim()
        val correctTrim = correctRaw.trim()

        val chosenN = normalizeForMatch(chosenTrim)
        val correctN = normalizeForMatch(correctTrim)

        val ok = chosenN.isNotBlank() && chosenN == correctN
        if (!ok) return AnswerEval(isCorrect = false, acceptanceNote = null)

        // Nota sutil só quando a resposta “bateu” via tolerância
        // (ou seja, não é exatamente igual ao esperado)
        val acceptedByTolerance = chosenTrim != correctTrim
        val note = if (acceptedByTolerance) {
            "Variação aceita (acentos, maiúsculas, espaços e pontuação são ignorados)."
        } else null

        return AnswerEval(isCorrect = true, acceptanceNote = note)
    }

    fun submitAnswer(
        optionRaw: String,
        responseMs: Long,
        hintCount: Int,
        onDone: (xp: Int, correct: Int, wrong: Int) -> Unit
    ) {
        val s = _state.value
        val sessionId = s.sessionId ?: return
        val challenge = s.challenges.getOrNull(s.index) ?: return
        if (s.phase != QuestionPhase.ANSWERING) return
        if (submitInFlight) return

        submitInFlight = true

        viewModelScope.launch {
            try {
                val chosen = optionRaw.trim()
                val eval = evalAnswer(chosen, challenge.correct)
                val isCorrect = eval.isCorrect

                val multiplier = hintMultiplier(hintCount)

                val xpAwarded = repo.recordAttempt(
                    sessionId = sessionId,
                    itemId = challenge.itemId,
                    isCorrect = isCorrect,
                    responseMs = responseMs,
                    chosen = chosen,
                    correct = challenge.correct,
                    hintCount = hintCount,
                    xpMultiplier = multiplier
                )

                val newCorrect = s.correct + if (isCorrect) 1 else 0
                val newWrong = s.wrong + if (!isCorrect) 1 else 0
                val newTotalXp = s.totalXp + xpAwarded

                val newSum = s.sumResponseMs + responseMs
                val newAttempts = s.attempts + 1

                _state.value = s.copy(
                    totalXp = newTotalXp,
                    correct = newCorrect,
                    wrong = newWrong,
                    lastWasCorrect = isCorrect,
                    lastXp = xpAwarded,
                    sumResponseMs = newSum,
                    attempts = newAttempts,
                    phase = QuestionPhase.REVEAL,
                    reveal = RevealUi(
                        chosen = chosen,
                        correct = challenge.correct,
                        isCorrect = isCorrect,
                        xpAwarded = xpAwarded,
                        hintCount = hintCount,
                        acceptanceNote = eval.acceptanceNote
                    )
                )
            } finally {
                submitInFlight = false
            }
        }
    }

    fun continueAfterReveal(onDone: (xp: Int, correct: Int, wrong: Int, sessionId: String) -> Unit) {
        val s = _state.value
        val sessionId = s.sessionId ?: return
        if (s.phase != QuestionPhase.REVEAL) return

        viewModelScope.launch {
            val nextIndex = s.index + 1
            val isLast = nextIndex >= s.challenges.size

            val avg = if (s.attempts <= 0) 0L else (s.sumResponseMs / max(1, s.attempts))

            if (isLast) {
                repo.finishSessionById(
                    sessionId = sessionId,
                    totalXp = s.totalXp,
                    avgResponseMs = avg,
                    correct = s.correct,
                    wrong = s.wrong,
                    abandoned = false
                )
                onDone(s.totalXp, s.correct, s.wrong, sessionId)
            } else {
                _state.value = s.copy(
                    index = nextIndex,
                    phase = QuestionPhase.ANSWERING,
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
                    repo.abortSessionById(
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
}
