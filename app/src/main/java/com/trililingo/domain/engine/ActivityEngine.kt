package com.trililingo.domain.engine

import com.trililingo.data.db.entities.LanguageItemEntity
import kotlin.random.Random

/**
 * Engine genérica: aqui ela gera Challenges a partir de itens.
 * No futuro você troca/expande para suportar outros ActivityType.
 */
object ActivityEngine {

    fun generateComicCompareChallenges(
        items: List<LanguageItemEntity>,
        length: Int
    ): List<Challenge> {
        if (items.isEmpty()) return emptyList()

        val shuffled = items.shuffled()
        val chosen = shuffled.take(length).ifEmpty { listOf(shuffled.first()) }

        return chosen.map { correctItem ->
            val distractors = (items - correctItem).shuffled().take(3).map { it.answer }
            val options = (distractors + correctItem.answer).shuffled()

            Challenge(
                itemId = correctItem.id,
                panelAsset = correctItem.panelAsset,
                prompt = correctItem.prompt,
                meaningHint = correctItem.meaning,
                options = options,
                correct = correctItem.answer
            )
        }
    }

    fun score(isCorrect: Boolean, responseMs: Long): Int {
        if (!isCorrect) return 0
        val base = 10
        val speedBonus = when {
            responseMs <= 1200 -> 6
            responseMs <= 2200 -> 3
            responseMs <= 4000 -> 1
            else -> 0
        }
        return base + speedBonus
    }
}
