package com.trililingo.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trililingo.data.db.entities.StudySessionEntity
import com.trililingo.data.repo.StudyRepository
import com.trililingo.data.repo.UserPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

data class DayProgressUi(
    val label: String,
    val date: LocalDate,
    val xp: Int,
    val sessions: Int,
    val minutes: Int
)

data class SessionRowUi(
    val title: String,
    val subtitle: String,
    val xp: Int,
    val correct: Int,
    val wrong: Int,
    val avgResponseMs: Long,
    val minutes: Int,
    val abandoned: Boolean,

    // ✅ NOVO: timestamp usado para agrupar/ordenar no modal
    val atMs: Long
)

data class AnalyticsUiState(
    val loading: Boolean = true,
    val streak: Int = 0,
    val totalXp: Long = 0L,

    val weekLabel: String = "",
    val weekXp: Int = 0,
    val weekSessions: Int = 0,
    val weekMinutes: Int = 0,
    val weekAccuracy: Int = 0,
    val weekAvgResponseMs: Long = 0L,
    val bestDayLabel: String = "",

    val byDay: List<DayProgressUi> = emptyList(),
    val languageBreakdown: List<Pair<String, Int>> = emptyList(),
    val latestSessions: List<SessionRowUi> = emptyList(),

    val error: String? = null
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    prefs: UserPrefsRepository,
    repo: StudyRepository
) : ViewModel() {

    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val weekOffset = MutableStateFlow(0)

    fun previousWeek() {
        weekOffset.value = weekOffset.value - 1
    }

    fun nextWeek() {
        if (weekOffset.value < 0) weekOffset.value = weekOffset.value + 1
    }

    fun resetWeek() {
        weekOffset.value = 0
    }

    val uiState: StateFlow<AnalyticsUiState> =
        combine(
            prefs.streakFlow,
            prefs.totalXpFlow,
            repo.latestSessions(),
            weekOffset
        ) { streak, totalXp, sessions, offset ->
            try {
                buildState(streak, totalXp, sessions, offset)
            } catch (t: Throwable) {
                AnalyticsUiState(
                    loading = false,
                    streak = streak,
                    totalXp = totalXp,
                    error = t.message ?: "Erro ao montar analytics"
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AnalyticsUiState())

    private fun buildState(
        streak: Int,
        totalXp: Long,
        sessions: List<StudySessionEntity>,
        offset: Int
    ): AnalyticsUiState {
        val today = LocalDate.now(zone)
        val monday = today
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(offset.toLong())
        val sunday = monday.plusDays(6)

        val labels = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

        val weekSessions = sessions.filter { s ->
            val day = toLocalDate(s)
            !day.isBefore(monday) && !day.isAfter(sunday)
        }

        val byDay = (0..6).map { idx ->
            val date = monday.plusDays(idx.toLong())
            val daySessions = weekSessions.filter { toLocalDate(it) == date }

            val xp = daySessions.sumOf { it.xpGained }
            val minutes = daySessions.sumOf { durationMinutes(it) }
            DayProgressUi(
                label = labels[idx],
                date = date,
                xp = xp,
                sessions = daySessions.size,
                minutes = minutes
            )
        }

        val weekXp = byDay.sumOf { it.xp }
        val weekCount = byDay.sumOf { it.sessions }
        val weekMinutes = byDay.sumOf { it.minutes }

        val totalCorrect = weekSessions.sumOf { it.correctCount }
        val totalWrong = weekSessions.sumOf { it.wrongCount }
        val attempts = totalCorrect + totalWrong
        val accuracy = if (attempts > 0) ((totalCorrect.toDouble() / attempts) * 100).roundToInt() else 0

        val weightedAvg = run {
            val totalAttempts = max(1, attempts)
            val sum = weekSessions.sumOf { s ->
                val a = max(0, s.correctCount + s.wrongCount)
                (s.avgResponseMs * a).toLong()
            }
            if (attempts > 0) (sum / totalAttempts).coerceAtLeast(0) else 0L
        }

        val best = byDay.maxByOrNull { it.xp } ?: DayProgressUi("Seg", monday, 0, 0, 0)
        val bestDayLabel = if (best.xp > 0) "${best.label} (${best.xp} XP)" else "—"

        val weekLabel = "${fmtDate(monday)} → ${fmtDate(sunday)}"

        val languageBreakdown = weekSessions
            .groupBy { it.language }
            .map { (lang, list) -> lang to list.sumOf { it.xpGained } }
            .sortedByDescending { it.second }
            .take(8)

        val latestSessionsUi = sessions
            .sortedByDescending { it.startedAtMs }
            .take(20)
            .map { s -> toRowUi(s) }

        return AnalyticsUiState(
            loading = false,
            streak = streak,
            totalXp = totalXp,
            weekLabel = weekLabel,
            weekXp = weekXp,
            weekSessions = weekCount,
            weekMinutes = weekMinutes,
            weekAccuracy = accuracy,
            weekAvgResponseMs = weightedAvg,
            bestDayLabel = bestDayLabel,
            byDay = byDay,
            languageBreakdown = languageBreakdown,
            latestSessions = latestSessionsUi,
            error = null
        )
    }

    private fun toLocalDate(s: StudySessionEntity): LocalDate {
        val ms = s.endedAtMs ?: s.startedAtMs
        return Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    }

    private fun durationMinutes(s: StudySessionEntity): Int {
        val end = s.endedAtMs ?: s.startedAtMs
        val durMs = (end - s.startedAtMs).coerceAtLeast(0)
        return (durMs / 60_000L).toInt()
    }

    private fun toRowUi(s: StudySessionEntity): SessionRowUi {
        val day = Instant.ofEpochMilli(s.startedAtMs).atZone(zone)
        val minutes = durationMinutes(s)

        val lang = s.language.uppercase()
        val type = s.activityType
        val title = "$lang • $type • ${fmtDateTime(day)}"

        val subtitle = "✅ ${s.correctCount}  ❌ ${s.wrongCount}  •  ${minutes}min  •  avg ${s.avgResponseMs}ms"

        return SessionRowUi(
            title = title,
            subtitle = subtitle,
            xp = s.xpGained,
            correct = s.correctCount,
            wrong = s.wrongCount,
            avgResponseMs = s.avgResponseMs,
            minutes = minutes,
            abandoned = s.abandoned,
            atMs = (s.endedAtMs ?: s.startedAtMs)
        )
    }

    private fun fmtDate(d: LocalDate): String {
        val f = DateTimeFormatter.ofPattern("dd/MM")
        return d.format(f)
    }

    private fun fmtDateTime(dt: java.time.ZonedDateTime): String {
        val f = DateTimeFormatter.ofPattern("dd/MM HH:mm")
        return dt.format(f)
    }
}
