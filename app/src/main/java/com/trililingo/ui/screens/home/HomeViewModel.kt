package com.trililingo.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trililingo.data.catalog.CatalogRepository
import com.trililingo.data.repo.UserPrefsRepository
import com.trililingo.ui.catalog.LanguageUi
import com.trililingo.ui.catalog.SubjectUi
import com.trililingo.ui.catalog.TrackUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeCatalogUiState(
    val subjects: List<SubjectUi>,
    val selectedSubjectId: String,
    val languages: List<LanguageUi>,
    val selectedLanguageCode: String?,
    val tracks: List<TrackUi>
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    prefs: UserPrefsRepository,
    private val catalogRepo: CatalogRepository
) : ViewModel() {

    val streak = prefs.streakFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val totalXp = prefs.totalXpFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val selectedSubjectId = MutableStateFlow("languages")
    private val selectedLanguageCode = MutableStateFlow("JA")

    val catalog: StateFlow<HomeCatalogUiState> = combine(
        catalogRepo.snapshot,
        selectedSubjectId,
        selectedLanguageCode
    ) { snap, subjectId, languageCode ->

        val availableSubjectIds = snap.subjects.map { it.id }.toSet()
        val finalSubjectId = if (subjectId in availableSubjectIds) subjectId
        else snap.subjects.firstOrNull()?.id ?: "languages"

        val finalLanguage = if (finalSubjectId == "languages") {
            val availableLangs = snap.languages.map { it.code }.toSet()
            if (languageCode in availableLangs) languageCode else snap.languages.firstOrNull()?.code ?: "JA"
        } else null

        HomeCatalogUiState(
            subjects = snap.subjects,
            selectedSubjectId = finalSubjectId,
            languages = snap.languages,
            selectedLanguageCode = finalLanguage,
            tracks = snap.tracksFor(finalSubjectId, finalLanguage)
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        HomeCatalogUiState(
            subjects = emptyList(),
            selectedSubjectId = "languages",
            languages = emptyList(),
            selectedLanguageCode = "JA",
            tracks = emptyList()
        )
    )

    init {
        viewModelScope.launch {
            catalogRepo.ensureLoaded()
        }
    }

    fun selectSubject(subjectId: String) {
        selectedSubjectId.value = subjectId
        if (subjectId != "languages") {
            selectedLanguageCode.value = "JA"
        }
    }

    fun selectLanguage(languageCode: String) {
        if (selectedSubjectId.value != "languages") return
        selectedLanguageCode.value = languageCode
    }
}
