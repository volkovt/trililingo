package com.trililingo.data.catalog

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * ✅ Serializador compatível:
 * - aceita "type": "ia"
 * - aceita "type": ["ia","definições"]
 *
 * Seu ia_fundamentals.json usa o formato lista.
 */
@OptIn(InternalSerializationApi::class) // <--- ADD THIS
object StringOrStringListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("StringOrStringList", StructureKind.LIST) {
            element("element", JsonPrimitive.serializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("StringOrStringListSerializer só funciona com JsonDecoder")
        val el = jsonDecoder.decodeJsonElement()

        return when (el) {
            is JsonPrimitive -> {
                if (el.isString) listOf(el.content) else emptyList()
            }

            is JsonArray -> el.mapNotNull { e ->
                runCatching { jsonDecoder.json.decodeFromJsonElement<String>(e) }.getOrNull()
            }

            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("StringOrStringListSerializer só funciona com JsonEncoder")
        val arr = JsonArray(value.map { JsonPrimitive(it) })
        jsonEncoder.encodeJsonElement(arr)
    }
}

@Serializable
data class SubjectPack(
    val packVersion: Int = 1,
    val subjectId: String? = null,
    val subjectName: String,
    val tracks: List<SubjectTrack> = emptyList()
)

@Serializable
data class SubjectTrack(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val enabled: Boolean = true,
    val features: List<SubjectFeature> = emptyList(),

    /**
     * ✅ Isso estava faltando e por isso seus capítulos nunca apareciam.
     */
    val studyContent: SubjectStudyContent? = null
)

@Serializable
data class SubjectFeature(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String = "",
    val emoji: String = "✨",
    val enabled: Boolean = true
)

@Serializable
data class SubjectStudyContent(
    val source: SubjectStudySource? = null,
    val chapters: List<SubjectStudyChapter> = emptyList()
)

@Serializable
data class SubjectStudySource(
    val book: String? = null,
    val authors: List<String> = emptyList(),
    val mdOutline: String? = null,
    val language: String? = null
)

@Serializable
data class SubjectStudyChapter(
    val chapter: Int? = null,
    val id: String,
    val title: String,
    val keyTopics: List<String> = emptyList(),
    val questions: List<SubjectStudyQuestion> = emptyList()
)

@Serializable
data class SubjectStudyQuestion(
    val id: String,

    /**
     * ✅ No ia_fundamentals.json é lista (["ia","definições"]).
     * Com esse serializer também aceitamos string para packs futuros.
     */
    @SerialName("type")
    @Serializable(with = StringOrStringListSerializer::class)
    val typeTags: List<String> = emptyList(),

    val prompt: String,
    val difficulty: Int = 1,
    val tags: List<String> = emptyList(),
    val expected: String? = null
)
