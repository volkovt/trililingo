package com.trililingo.domain.subject

import com.trililingo.data.catalog.SubjectStudyQuestion
import java.security.MessageDigest
import kotlin.random.Random

enum class SubjectStudyMode {
    DAILY,
    FREE
}

object SubjectStudyEngine {

    fun buildSession(
        mode: SubjectStudyMode,
        all: List<SubjectStudyQuestion>,
        dayEpoch: Long,
        seedKey: String,
        dailyLimit: Int = 10
    ): List<SubjectStudyQuestion> {
        if (all.isEmpty()) return emptyList()

        return when (mode) {
            SubjectStudyMode.DAILY -> {
                val rng = Random(stableSeed("$dayEpoch|$seedKey|DAILY"))
                all.shuffled(rng).take(minOf(dailyLimit, all.size))
            }
            SubjectStudyMode.FREE -> {
                val rng = Random(stableSeed("${System.currentTimeMillis()}|$seedKey|FREE"))
                all.shuffled(rng)
            }
        }
    }

    private fun stableSeed(key: String): Int {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
        // pega 4 bytes p/ int
        var x = 0
        for (i in 0 until 4) {
            x = (x shl 8) or (bytes[i].toInt() and 0xff)
        }
        return x
    }
}
