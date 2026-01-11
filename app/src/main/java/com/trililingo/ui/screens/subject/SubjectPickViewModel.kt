package com.trililingo.ui.screens.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trililingo.data.catalog.SubjectStudyChapter
import com.trililingo.data.repo.SubjectStudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubjectPickState(
    val loading: Boolean = true,
    val error: String? = null,
    val chapters: List<SubjectStudyChapter> = emptyList(),
    val selectedChapterId: String? = null
)

@HiltViewModel
class SubjectPickViewModel @Inject constructor(
    private val repo: SubjectStudyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SubjectPickState())
    val state: StateFlow<SubjectPickState> = _state

    fun load(subjectId: String, trackId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                repo.getChapters(subjectId, trackId)
            }.onSuccess { chapters ->
                _state.value = SubjectPickState(
                    loading = false,
                    chapters = chapters,
                    selectedChapterId = null
                )
            }.onFailure { e ->
                _state.value = SubjectPickState(
                    loading = false,
                    error = e.message ?: "Erro ao carregar capítulos",
                    chapters = emptyList()
                )
            }
        }
    }

    fun selectChapter(chapterId: String?) {
        _state.value = _state.value.copy(selectedChapterId = chapterId)
    }
}
