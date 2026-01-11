package com.trililingo.domain.engine

data class ActivityDefinition(
    val activityType: String,  // "COMIC_COMPARE"
    val language: String,      // "JA" | "ZH"
    val skill: String,         // "HIRAGANA" etc.
    val length: Int            // número de desafios na sessão
)

data class Challenge(
    val itemId: String,
    val panelAsset: String?,
    val prompt: String,             // caractere grande
    val meaningHint: String,        // hint
    val options: List<String>,      // alternativas
    val correct: String
)

data class ChallengeResult(
    val isCorrect: Boolean,
    val xpEarned: Int,
    val responseMs: Long
)
