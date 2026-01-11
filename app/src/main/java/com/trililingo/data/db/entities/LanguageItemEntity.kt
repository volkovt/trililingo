package com.trililingo.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "language_items",
    indices = [
        Index("language"),
        Index("skill")
    ]
)
data class LanguageItemEntity(
    @PrimaryKey val id: String,
    val language: String, // "JA" | "ZH"
    val skill: String,    // "HIRAGANA" | "KATAKANA" | "KANJI" | "HANZI"
    val prompt: String,   // caractere (あ, 一, etc.)
    val answer: String,   // leitura (a, yī, etc.)
    val meaning: String,  // dica/meaning
    val panelAsset: String? // caminho em assets se tiver
)
