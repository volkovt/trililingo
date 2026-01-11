package com.trililingo.data.catalog

import android.content.Context
import com.trililingo.ui.catalog.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val mutex = Mutex()
    private var loaded = false

    private val _snapshot = MutableStateFlow(
        CatalogSnapshot(
            subjects = listOf(SubjectUi(id = "languages", title = "Idiomas", kind = SubjectKind.LANGUAGE)),
            languages = defaultLanguages(),
            allTracks = defaultLanguageTracks()
        )
    )
    val snapshot: StateFlow<CatalogSnapshot> = _snapshot

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            refreshInternal()
            loaded = true
        }
    }

    suspend fun refresh() {
        mutex.withLock {
            refreshInternal()
            loaded = true
        }
    }

    private suspend fun refreshInternal() = withContext(Dispatchers.IO) {
        val loader = SubjectPacksLoader(ctx)
        val packs = loader.load().packs

        val validPacks = packs
            .asSequence()
            .filter { it.subjectName.isNotBlank() }
            .filter { isSubjectPackValidForNow(it) }
            .toList()

        val dynamicSubjects = validPacks
            .map { p ->
                SubjectUi(
                    id = p.subjectId ?: p.subjectName.lowercase().replace(" ", "_"),
                    title = p.subjectName,
                    kind = SubjectKind.TOPIC
                )
            }

        val dynamicTracks = validPacks.flatMap { p ->
            val subjectId = p.subjectId ?: p.subjectName.lowercase().replace(" ", "_")

            p.tracks
                .filter { it.enabled }
                .map { t ->
                    TrackUi(
                        id = com.trililingo.ui.catalog.TopicTrackIds.encode(subjectId, t.id),
                        subjectId = subjectId,
                        title = t.title,
                        subtitle = t.subtitle,
                        languageCode = null,
                        skillCode = null,
                        enabled = t.enabled,
                        features = t.features
                            .filter { it.enabled }
                            .mapNotNull { f ->
                                when (f.type.trim().uppercase()) {
                                    "DAILY_CHALLENGE" -> FeatureUi(
                                        id = f.id,
                                        type = FeatureType.DAILY_CHALLENGE,
                                        title = f.title,
                                        subtitle = f.subtitle,
                                        emoji = f.emoji,
                                        enabled = f.enabled
                                    )

                                    "STUDY" -> FeatureUi(
                                        id = f.id,
                                        type = FeatureType.STUDY,
                                        title = f.title,
                                        subtitle = f.subtitle,
                                        emoji = f.emoji,
                                        enabled = f.enabled
                                    )

                                    else -> null
                                }
                            }
                    )
                }
        }

        val subjects = buildList {
            add(SubjectUi(id = "languages", title = "Idiomas", kind = SubjectKind.LANGUAGE))
            addAll(dynamicSubjects)
        }.distinctBy { it.id }

        _snapshot.value = CatalogSnapshot(
            subjects = subjects,
            languages = defaultLanguages(),
            allTracks = defaultLanguageTracks() + dynamicTracks
        )
    }

    private fun mapTopicFeatureToUi(f: SubjectFeature): FeatureUi? {
        return when (f.type.trim().uppercase()) {
            "DAILY_CHALLENGE" -> FeatureUi(
                id = f.id,
                type = FeatureType.DAILY_CHALLENGE,
                title = f.title,
                subtitle = f.subtitle,
                emoji = f.emoji,
                enabled = f.enabled
            )

            "STUDY" -> FeatureUi(
                id = f.id,
                type = FeatureType.STUDY,
                title = f.title,       // "Estudo Livre"
                subtitle = f.subtitle, // "Sessão aberta de estudo"
                emoji = f.emoji,
                enabled = f.enabled
            )

            else -> null
        }
    }

    private fun isSubjectPackValidForNow(pack: SubjectPack): Boolean {
        val tracks = pack.tracks.filter { it.enabled }
        if (tracks.isEmpty()) return false

        for (t in tracks) {
            val enabledTypes = t.features
                .filter { it.enabled }
                .map { it.type.trim().uppercase() }
                .toSet()

            if (enabledTypes != REQUIRED_SUBJECT_FEATURE_TYPES) return false
        }
        return true
    }

    private fun defaultLanguages(): List<LanguageUi> = listOf(
        LanguageUi(code = "JA", title = "Japonês", flagEmoji = "🇯🇵"),
        LanguageUi(code = "ZH", title = "Chinês", flagEmoji = "🇨🇳"),
        LanguageUi(code = "RU", title = "Russo", flagEmoji = "🇷🇺")
    )

    // ✅ nada muda
    private fun languageFeatures(language: String, skill: String): List<FeatureUi> {
        val base = listOf(
            FeatureUi("daily_challenge", FeatureType.DAILY_CHALLENGE, "Desafio diário", "XP + streak • usa seu conjunto se configurado", "⚡", true),
            FeatureUi("daily_set", FeatureType.DAILY_SET, "Conjunto do diário", "Escolha no mínimo 10 itens", "🧩", true),
            FeatureUi("alphabet", FeatureType.ALPHABET, "Alfabeto", "Tabela completa", "🔤", true),
            FeatureUi("practice", FeatureType.PRACTICE, "Treino livre", "Sessões rápidas", "🎮", true),
            FeatureUi("dictionary", FeatureType.DICTIONARY, "Dicionário", "Itens e significados", "📚", false),
            FeatureUi("study", FeatureType.STUDY, "Estudos", "Explicações e dicas", "🧠", false)
        )
        return if (skill == "KANJI" || skill == "HANZI") {
            base.map { f -> if (f.type == FeatureType.ALPHABET) f.copy(title = "Lista", subtitle = "Caracteres do pack") else f }
        } else base
    }

    private fun defaultLanguageTracks(): List<TrackUi> = listOf(
        TrackUi("ja_hiragana", "languages", "Hiragana", "Comic Compare", "JA", "HIRAGANA", true, languageFeatures("JA", "HIRAGANA")),
        TrackUi("ja_katakana", "languages", "Katakana", "Comic Compare", "JA", "KATAKANA", true, languageFeatures("JA", "KATAKANA")),
        TrackUi("ja_kanji", "languages", "Kanji", "Comic Compare", "JA", "KANJI", true, languageFeatures("JA", "KANJI")),
        TrackUi("zh_hanzi", "languages", "Hanzi", "Comic Compare", "ZH", "HANZI", true, languageFeatures("ZH", "HANZI")),
        TrackUi("ru_alphabet", "languages", "Alfabeto", "Cirílico • sons e letras", "RU", "ALPHABET", true, languageFeatures("RU", "ALPHABET")),
        TrackUi("ru_words", "languages", "Palavras", "Vocabulário essencial", "RU", "WORDS", true, languageFeatures("RU", "WORDS"))
    )

    private companion object {
        val REQUIRED_SUBJECT_FEATURE_TYPES = setOf("DAILY_CHALLENGE", "STUDY")
    }
}
