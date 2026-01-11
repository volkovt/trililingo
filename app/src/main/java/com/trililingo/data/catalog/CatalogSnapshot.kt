package com.trililingo.data.catalog

import com.trililingo.ui.catalog.LanguageUi
import com.trililingo.ui.catalog.SubjectUi
import com.trililingo.ui.catalog.TrackUi

data class CatalogSnapshot(
    val subjects: List<SubjectUi>,
    val languages: List<LanguageUi>,
    val allTracks: List<TrackUi>
) {
    fun tracksFor(subjectId: String, languageCode: String?): List<TrackUi> {
        return if (subjectId == "languages") {
            val lang = languageCode ?: languages.firstOrNull()?.code
            allTracks.filter { it.subjectId == "languages" && it.languageCode == lang }
        } else {
            allTracks.filter { it.subjectId == subjectId }
        }
    }

    fun trackById(trackId: String): TrackUi? =
        allTracks.firstOrNull { it.id == trackId }
}
