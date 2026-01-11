package com.trililingo.data.catalog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.security.MessageDigest

class SubjectPacksLoader(
    private val ctx: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val prefs by lazy {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    data class LoadResult(
        val signature: String,
        val packs: List<SubjectPack>
    )

    suspend fun load(): LoadResult = withContext(Dispatchers.IO) {
        val root = "packs/subjects"
        val files = ctx.assets
            .list(root)
            ?.filter { it.endsWith(".json", ignoreCase = true) }
            ?.sorted()
            .orEmpty()

        if (files.isEmpty()) {
            return@withContext LoadResult(signature = "EMPTY", packs = emptyList())
        }

        val signature = computeAssetsSignature(root, files)

        val cachedSig = prefs.getString(KEY_LAST_SIGNATURE, null)
        val cachedRaw = prefs.getString(KEY_LAST_PAYLOAD, null)

        if (cachedSig == signature && !cachedRaw.isNullOrBlank()) {
            val cached = runCatching {
                json.decodeFromString(ListSerializer.subjectPackList(), cachedRaw)
            }.getOrNull()

            if (cached != null) {
                return@withContext LoadResult(signature = signature, packs = cached)
            }
        }

        val packs = buildList {
            for (file in files) {
                val raw = ctx.assets.open("$root/$file").bufferedReader().use { it.readText() }
                val pack = json.decodeFromString(SubjectPack.serializer(), raw)

                val idFromFilename = file.substringBeforeLast('.')
                val final = pack.copy(subjectId = pack.subjectId ?: idFromFilename)

                add(final)
            }
        }

        val payload = json.encodeToString(ListSerializer.subjectPackList(), packs)

        prefs.edit()
            .putString(KEY_LAST_SIGNATURE, signature)
            .putString(KEY_LAST_PAYLOAD, payload)
            .apply()

        LoadResult(signature = signature, packs = packs)
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

    private fun ByteArray.toHex(): String = joinToString("") { b -> "%02x".format(b) }

    private object ListSerializer {
        fun subjectPackList() =
            kotlinx.serialization.builtins.ListSerializer(SubjectPack.serializer())
    }

    private companion object {
        private const val PREFS_NAME = "subject_packs_cache"
        private const val KEY_LAST_SIGNATURE = "last_subject_signature"
        private const val KEY_LAST_PAYLOAD = "last_subject_payload"
    }
}
