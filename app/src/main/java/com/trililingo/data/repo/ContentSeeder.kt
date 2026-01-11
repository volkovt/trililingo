package com.trililingo.data.repo

import android.content.Context
import com.trililingo.data.db.TrililingoDb
import com.trililingo.data.db.entities.LanguageItemEntity
import com.trililingo.data.db.entities.SrsStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class ItemMeta(
    val id: String,
    val language: String,
    val skill: String,
    val category: String? = null,
    val difficulty: Int? = null,
    val romanization: RomanizationMeta? = null,
    val pronunciationPt: String?,
    val gojuon: GojuonMeta? = null,
    val mnemonicPt: String? = null,
    val tags: List<String> = emptyList(),
    val distractorIds: List<String> = emptyList(),
    val assets: AssetsMeta? = null,
    val notes: NotesMeta? = null
)

@Serializable
data class RomanizationMeta(
    val system: String? = null,
    val value: String? = null
)

@Serializable
data class GojuonMeta(
    val rowKey: String? = null,
    val rowLabel: String? = null,
    val vowel: String? = null,
    val row: Int? = null,
    val col: Int? = null,
    val indexApprox: Int? = null,
    val isStandardCell: Boolean? = null
)

@Serializable
data class AssetsMeta(
    val audio: String? = null,
    val strokeOrder: String? = null,
    val panel: String? = null
)

@Serializable
data class NotesMeta(
    val pt: String? = null,
    val en: String? = null
)

class ContentSeeder(
    private val ctx: Context,
    private val db: TrililingoDb
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val seedMutex = Mutex()

    private val prefs by lazy {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Serializable
    private data class Pack(
        val packVersion: Int,
        val language: String,
        val items: List<Item>
    )

    @Serializable
    private data class Item(
        val id: String,
        val skill: String,
        val prompt: String,
        val answer: String,
        val meaning: String,
        val panelAsset: String? = null,
        val language: String? = null,

        val category: String? = null,
        val difficulty: Int? = null,
        val romanization: RomanizationMeta? = null,

        val pronunciationPt: String? = null,

        val gojuon: GojuonMeta? = null,
        val mnemonicPt: String? = null,
        val tags: List<String> = emptyList(),
        val distractorIds: List<String> = emptyList(),
        val assets: AssetsMeta? = null,
        val notes: NotesMeta? = null
    )


    // ✅ cache de metadata por assinatura do assets
    private var metaSignature: String? = null
    private var metaIndex: Map<String, ItemMeta> = emptyMap()

    /**
     * Roda o seed de forma idempotente e eficiente:
     * - Calcula uma assinatura dos JSONs do assets
     * - Se a assinatura for igual à última aplicada -> não faz nada
     * - Se mudou -> faz upsert do conteúdo e cria SRS somente para itens novos
     */
    suspend fun ensureSeeded(force: Boolean = false) = seedMutex.withLock {
        val packsRoot = "packs/lang"
        val packFiles = ctx.assets
            .list(packsRoot)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            .orEmpty()

        if (packFiles.isEmpty()) {
            throw IllegalStateException("Nenhum pack encontrado em assets/$packsRoot")
        }

        val currentSignature = computeAssetsSignature(packsRoot, packFiles)
        val lastSignature = prefs.getString(KEY_LAST_SIGNATURE, null)

        if (!force && lastSignature == currentSignature) return@withLock

        val entities = buildList {
            for (file in packFiles) {
                val raw = ctx.assets.open("$packsRoot/$file")
                    .bufferedReader()
                    .use { it.readText() }

                val pack = json.decodeFromString(Pack.serializer(), raw)
                val packLang = pack.language

                addAll(
                    pack.items.map { it ->
                        LanguageItemEntity(
                            id = it.id,
                            language = it.language ?: packLang,
                            skill = it.skill,
                            prompt = it.prompt,
                            answer = it.answer,
                            meaning = it.meaning,
                            panelAsset = it.panelAsset
                        )
                    }
                )
            }
        }

        val deduped = entities
            .groupBy { it.id }
            .mapValues { (_, v) -> v.first() }
            .values
            .toList()

        db.languageItemDao().upsertAll(deduped)

        val ids = deduped.map { it.id }
        val existingIds = db.srsStateDao().getExistingItemIds(ids).toSet()
        val newIds = ids.filterNot { it in existingIds }

        if (newIds.isNotEmpty()) {
            val newStates = newIds.map { id ->
                SrsStateEntity(
                    itemId = id,
                    ease = 2.5,
                    intervalDays = 0,
                    repetitions = 0,
                    lapses = 0,
                    dueAtMs = 0L
                )
            }

            db.srsStateDao().insertIgnore(newStates)
        }

        prefs.edit()
            .putString(KEY_LAST_SIGNATURE, currentSignature)
            .putLong(KEY_LAST_APPLIED_AT, System.currentTimeMillis())
            .apply()

        // ✅ invalida meta cache quando houver reseed
        metaSignature = null
        metaIndex = emptyMap()
    }

    /**
     * Metadata rica para UI/engine (cacheado por assinatura do assets).
     * Não precisa persistir em Room: o JSON já é offline e a assinatura garante consistência.
     */
    suspend fun getMetaByIds(ids: List<String>): Map<String, ItemMeta> = withContext(Dispatchers.IO) {
        val index = loadMetaIndex()
        ids.distinct()
            .mapNotNull { id -> index[id]?.let { id to it } }
            .toMap()
    }

    suspend fun getMetaIndex(): Map<String, ItemMeta> = withContext(Dispatchers.IO) {
        loadMetaIndex()
    }

    private suspend fun loadMetaIndex(): Map<String, ItemMeta> = seedMutex.withLock {
        val packsRoot = "packs/lang"
        val packFiles = ctx.assets
            .list(packsRoot)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            .orEmpty()

        val signature = computeAssetsSignature(packsRoot, packFiles)
        if (metaSignature == signature && metaIndex.isNotEmpty()) return@withLock metaIndex

        val index = buildMap<String, ItemMeta> {
            for (file in packFiles) {
                val raw = ctx.assets.open("$packsRoot/$file").bufferedReader().use { it.readText() }
                val pack = json.decodeFromString(Pack.serializer(), raw)
                val packLang = pack.language

                for (it in pack.items) {
                    val lang = it.language ?: packLang
                    put(
                        it.id,
                        ItemMeta(
                            id = it.id,
                            language = lang,
                            skill = it.skill,
                            category = it.category,
                            difficulty = it.difficulty,
                            romanization = it.romanization,
                            pronunciationPt = it.pronunciationPt,
                            gojuon = it.gojuon,
                            mnemonicPt = it.mnemonicPt,
                            tags = it.tags,
                            distractorIds = it.distractorIds,
                            assets = it.assets,
                            notes = it.notes
                        )
                    )
                }
            }
        }

        metaSignature = signature
        metaIndex = index
        index
    }

    /**
     * Útil em debug/QA para forçar um reseed na próxima abertura do app.
     */
    fun invalidateSeedCache() {
        prefs.edit().remove(KEY_LAST_SIGNATURE).apply()
        metaSignature = null
        metaIndex = emptyMap()
    }

    private fun computeAssetsSignature(root: String, files: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")

        for (file in files) {
            digest.update(file.toByteArray(Charsets.UTF_8))
            ctx.assets.open("$root/$file").use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
        }

        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { b -> "%02x".format(b) }

    private companion object {
        private const val PREFS_NAME = "content_seeder"
        private const val KEY_LAST_SIGNATURE = "last_assets_signature"
        private const val KEY_LAST_APPLIED_AT = "last_applied_at_ms"
    }
}
