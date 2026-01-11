package com.trililingo.ui.screens.track

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trililingo.data.catalog.CatalogRepository
import com.trililingo.ui.catalog.TrackUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackHubViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepo: CatalogRepository
) : ViewModel() {

    private val trackId: String = savedStateHandle.get<String>("trackId") ?: ""

    val track: StateFlow<TrackUi?> = catalogRepo.snapshot
        .map { it.trackById(trackId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { catalogRepo.ensureLoaded() }
    }
}
