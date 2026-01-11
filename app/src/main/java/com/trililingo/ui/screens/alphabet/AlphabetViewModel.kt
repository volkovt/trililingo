package com.trililingo.ui.screens.alphabet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trililingo.data.db.entities.LanguageItemEntity
import com.trililingo.data.repo.ItemMeta
import com.trililingo.data.repo.StudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlphabetRowUi(
    val entity: LanguageItemEntity,
    val meta: ItemMeta?
)

data class AlphabetUiState(
    val loading: Boolean = true,
    val items: List<AlphabetRowUi> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class AlphabetViewModel @Inject constructor(
    private val repo: StudyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AlphabetUiState())
    val state: StateFlow<AlphabetUiState> = _state

    fun load(language: String, skill: String) {
        viewModelScope.launch {
            try {
                _state.value = AlphabetUiState(loading = true)

                val items = repo.loadAlphabet(language, skill)
                val meta = repo.loadItemMeta(items.map { it.id })

                val rows = items.map { e -> AlphabetRowUi(e, meta[e.id]) }

                _state.value = AlphabetUiState(
                    loading = false,
                    items = rows,
                    error = null
                )
            } catch (t: Throwable) {
                _state.value = AlphabetUiState(
                    loading = false,
                    items = emptyList(),
                    error = t.message ?: "Erro ao carregar conteúdo"
                )
            }
        }
    }
}
