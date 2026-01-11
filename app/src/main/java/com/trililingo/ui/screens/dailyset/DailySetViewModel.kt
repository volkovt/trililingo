// DailySetViewModel.kt
package com.trililingo.ui.screens.dailyset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trililingo.core.language.PronunciationPtResolver
import com.trililingo.data.db.entities.LanguageItemEntity
import com.trililingo.data.repo.ItemMeta
import com.trililingo.data.repo.StudyRepository
import com.trililingo.data.repo.UserPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DailySetItemUi(
    val id: String,
    val prompt: String,
    val answer: String,
    val meaning: String?,
    val meta: ItemMeta?,

    // ✅ pronúncia aproximada PT-BR (quando existir no pack ou via fallback por idioma)
    val pronunciationPt: String?
) {
    fun searchableText(): String {
        val m = meta
        return buildString {
            append(prompt).append(' ')
            append(answer).append(' ')
            if (!meaning.isNullOrBlank()) append(meaning).append(' ')
            if (!pronunciationPt.isNullOrBlank()) append(pronunciationPt).append(' ')
            val romaji = m?.romanization?.value
            if (!romaji.isNullOrBlank()) append(romaji).append(' ')
            if (!m?.category.isNullOrBlank()) append(m?.category).append(' ')
            if (!m?.mnemonicPt.isNullOrBlank()) append(m?.mnemonicPt).append(' ')
            m?.tags?.forEach { append(it).append(' ') }
        }
    }
}

data class DailySetUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val minRequired: Int = 10,
    val items: List<DailySetItemUi> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val error: String? = null
)

@HiltViewModel
class DailySetViewModel @Inject constructor(
    private val repo: StudyRepository,
    private val prefs: UserPrefsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DailySetUiState())
    val state: StateFlow<DailySetUiState> = _state

    fun load(language: String, skill: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(loading = true, error = null)

                val entities: List<LanguageItemEntity> = repo.loadAlphabet(language, skill)
                val ids = entities.map { it.id }.distinct()

                val metaById = repo.loadItemMeta(ids)

                val uiItems = entities
                    .map { e ->
                        val meta = metaById[e.id]

                        DailySetItemUi(
                            id = e.id,
                            prompt = e.prompt,
                            answer = e.answer,
                            meaning = e.meaning,
                            meta = meta,

                            // ✅ Escalável:
                            // 1) usa meta.pronunciationPt (curado no JSON)
                            // 2) fallback específico por idioma quando fizer sentido
                            pronunciationPt = PronunciationPtResolver.resolve(
                                languageCode = language,
                                itemPronunciationPt = meta?.pronunciationPt,
                                romanization = meta?.romanization?.value,
                                fallbackText = e.answer
                            )
                        )
                    }
                    // ordenação “boa” quando existir gojuon index (senão cai no prompt)
                    .sortedWith(
                        compareBy<DailySetItemUi> { it.meta?.gojuon?.indexApprox ?: Int.MAX_VALUE }
                            .thenBy { it.prompt }
                    )

                val current = prefs.getDailySelection(language, skill)

                // remove IDs inválidos (se o pack mudou)
                val validSelected = current.itemIds
                    .distinct()
                    .filter { it in ids }
                    .toSet()

                _state.value = _state.value.copy(
                    loading = false,
                    enabled = current.enabled,
                    items = uiItems,
                    selectedIds = validSelected,
                    error = null
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    items = emptyList(),
                    selectedIds = emptySet(),
                    error = t.message ?: "Erro ao carregar conjunto do diário"
                )
            }
        }
    }

    fun toggleEnabled() {
        _state.value = _state.value.copy(enabled = !_state.value.enabled)
    }

    fun toggleItem(id: String) {
        val s = _state.value
        val next = if (id in s.selectedIds) (s.selectedIds - id) else (s.selectedIds + id)
        _state.value = s.copy(selectedIds = next)
    }

    fun selectAll() {
        val s = _state.value
        _state.value = s.copy(selectedIds = s.items.map { it.id }.toSet())
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedIds = emptySet())
    }

    fun save(language: String, skill: String, onSaved: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            val ids = s.selectedIds.toList()

            val finalEnabled = s.enabled && ids.size >= s.minRequired
            prefs.setDailySelection(
                language = language,
                skill = skill,
                enabled = finalEnabled,
                itemIds = ids
            )

            onSaved()
        }
    }
}
