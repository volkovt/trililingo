package com.trililingo.ui.catalog

object CatalogIds {
    const val SUBJECT_LANGUAGES = "languages"
}

enum class SubjectKind {
    LANGUAGE,
    TOPIC
}

data class SubjectUi(
    val id: String,
    val title: String,
    val kind: SubjectKind
)

data class LanguageUi(
    val code: String,
    val title: String,
    val flagEmoji: String
)

enum class FeatureType {
    DAILY_CHALLENGE,
    DAILY_SET,
    ALPHABET,
    PRACTICE,
    DICTIONARY,
    STUDY
}

data class FeatureUi(
    val id: String,
    val type: FeatureType,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val enabled: Boolean = true
)

data class TrackUi(
    val id: String,
    val subjectId: String,
    val title: String,
    val subtitle: String,
    val languageCode: String? = null,
    val skillCode: String? = null,
    val enabled: Boolean = true,
    val features: List<FeatureUi> = emptyList()
)