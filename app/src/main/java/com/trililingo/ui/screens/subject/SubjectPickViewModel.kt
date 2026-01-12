package com.trililingo.ui.screens.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trililingo.data.catalog.CatalogRepository
import com.trililingo.data.catalog.SubjectStudyChapter
import com.trililingo.data.repo.SubjectStudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class SubjectChapterUi(
    val id: String,
    val chapterNumber: Int?,
    val title: String,
    val keyTopics: List<String>,
    val questionCountTotal: Int,
    val questionCountValid: Int,
    val distinctTypeTags: List<String>,
    val avgDifficulty: Int
) {
    val headerLabel: String
        get() = (chapterNumber?.let { "Cap. $it — " } ?: "") + title
}

data class SubjectPickState(
    val loading: Boolean = true,
    val error: String? = null,

    val subjectTitle: String = "",
    val trackTitle: String = "",
    val trackSubtitle: String = "",

    val chapters: List<SubjectChapterUi> = emptyList(),
    val availableTypeTags: List<String> = emptyList(),

    val selectedChapterId: String? = null
)

@HiltViewModel
class SubjectPickViewModel @Inject constructor(
    private val repo: SubjectStudyRepository,
    private val catalog: CatalogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SubjectPickState())
    val state: StateFlow<SubjectPickState> = _state

    fun load(subjectId: String, trackId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)

            runCatching {
                catalog.ensureLoaded()
                val snap = catalog.snapshot.value

                val subjectTitle = snap.subjects.firstOrNull { it.id == subjectId }?.title ?: subjectId
                val trackUi = snap.allTracks.firstOrNull { it.id == trackId }

                val chapters = repo.getChapters(subjectId, trackId)
                val ui = chapters.map { ch -> ch.toUi() }

                val tags = ui.flatMap { it.distinctTypeTags }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                SubjectPickState(
                    loading = false,
                    error = null,
                    subjectTitle = subjectTitle,
                    trackTitle = trackUi?.title ?: trackId,
                    trackSubtitle = trackUi?.subtitle.orEmpty(),
                    chapters = ui,
                    availableTypeTags = tags,
                    selectedChapterId = null
                )
            }.onSuccess { s ->
                _state.value = s
            }.onFailure { e ->
                _state.value = SubjectPickState(
                    loading = false,
                    error = e.message ?: "Erro ao carregar capítulos"
                )
            }
        }
    }

    fun selectChapter(chapterId: String?) {
        _state.value = _state.value.copy(selectedChapterId = chapterId)
    }

    private fun SubjectStudyChapter.toUi(): SubjectChapterUi {
        val total = questions.size
        val valid = questions.count { q ->
            q.expected.any { it.trim().isNotBlank() }
        }

        val tags = questions
            .flatMap { it.typeTags }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        val avgDiff = if (questions.isEmpty()) 1 else {
            val avg = questions.map { it.difficulty.coerceIn(1, 5) }.average()
            avg.roundToInt().coerceIn(1, 5)
        }

        return SubjectChapterUi(
            id = id,
            chapterNumber = chapter,
            title = title,
            keyTopics = keyTopics,
            questionCountTotal = total,
            questionCountValid = valid,
            distinctTypeTags = tags,
            avgDifficulty = avgDiff
        )
    }
}
