package com.trililingo.domain.subject

enum class StudyDifficulty(val id: String, val title: String, val subtitle: String) {
    BASIC("basic", "Básico", "Sempre 4 opções"),
    MIXED("mixed", "Misto", "Opções OU digitar"),
    HARDCORE("hardcore", "Hardcore", "Somente digitar");

    companion object {
        fun fromId(raw: String?): StudyDifficulty {
            val v = raw?.trim()?.lowercase()
            return entries.firstOrNull { it.id == v } ?: BASIC
        }
    }
}

enum class AnswerMode { MULTIPLE_CHOICE, TYPING }
