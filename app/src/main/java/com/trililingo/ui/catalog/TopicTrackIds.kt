package com.trililingo.ui.catalog

object TopicTrackIds {
    private const val PREFIX = "topic::"

    fun encode(subjectId: String, trackId: String): String =
        "$PREFIX$subjectId::$trackId"

    fun decode(uiTrackId: String): Decoded? {
        if (!uiTrackId.startsWith(PREFIX)) return null
        val raw = uiTrackId.removePrefix(PREFIX)
        val parts = raw.split("::")
        if (parts.size != 2) return null
        return Decoded(subjectId = parts[0], trackId = parts[1])
    }

    data class Decoded(
        val subjectId: String,
        val trackId: String
    )
}
