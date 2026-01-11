package com.trililingo.core.language

/**
 * Resolver único e escalável para pronúncia aproximada em PT-BR.
 *
 * Regra:
 * 1) Se o item já tem pronunciationPt (curado no JSON), usa ele.
 * 2) Se não tiver, tenta fallback específico por idioma.
 * 3) Se não conseguir, retorna null.
 */
object PronunciationPtResolver {

    /**
     * @param languageCode Ex: "ZH", "JA"
     * @param itemPronunciationPt Campo pronunciationPt vindo do JSON (preferível)
     * @param romanization Ex: pinyin/hepburn/etc (caso queira fallback)
     * @param fallbackText Um texto alternativo (ex.: a própria opção correta, se fizer sentido)
     */
    fun resolve(
        languageCode: String,
        itemPronunciationPt: String?,
        romanization: String?,
        fallbackText: String?
    ): String? {
        val curated = itemPronunciationPt?.trim().orEmpty()
        if (curated.isNotBlank()) return curated

        val lang = languageCode.trim().uppercase()

        return when (lang) {
            "ZH" -> {
                val base = (romanization ?: fallbackText).orEmpty().trim()
                if (base.isBlank()) null else PinyinPtPronouncer.toPt(base).trim().ifBlank { null }
            }

            else -> null
        }
    }
}
