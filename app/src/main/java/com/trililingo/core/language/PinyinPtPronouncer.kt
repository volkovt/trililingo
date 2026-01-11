package com.trililingo.core.language

/**
 * Converte pinyin (com ou sem marcas de tom) para uma aproximação fonética
 * em escrita PT-BR. A ideia é ajudar iniciantes brasileiros a “lerem” a pronúncia
 * mesmo sem conhecer as regras do pinyin.
 *
 * Regras (intencionais, simples e estáveis):
 * - Separa sílabas com hífen (ex: nǐhǎo -> ni-hao)
 * - Mapeia consoantes “difíceis” para grafia mais familiar:
 *   zh -> j,  ch -> tch,  sh -> x,  q -> tch,  j -> dj,  z -> dz,  c -> ts,  r -> j
 * - y e w são tratados como glides:
 *   y + i... não duplica (yī -> i), w + u... não duplica (wǔ -> u)
 *
 * Observação: isso NÃO é IPA e nem pretende ser perfeito — é um guia prático para PT-BR.
 */
object PinyinPtPronouncer {

    private val toneStripMap: Map<Char, Char> = mapOf(
        'ā' to 'a', 'á' to 'a', 'ǎ' to 'a', 'à' to 'a',
        'ē' to 'e', 'é' to 'e', 'ě' to 'e', 'è' to 'e',
        'ī' to 'i', 'í' to 'i', 'ǐ' to 'i', 'ì' to 'i',
        'ō' to 'o', 'ó' to 'o', 'ǒ' to 'o', 'ò' to 'o',
        'ū' to 'u', 'ú' to 'u', 'ǔ' to 'u', 'ù' to 'u',
        'ǖ' to 'ü', 'ǘ' to 'ü', 'ǚ' to 'ü', 'ǜ' to 'ü',
        // casos raros em pinyin
        'ń' to 'n', 'ň' to 'n', 'ǹ' to 'n',
        'ḿ' to 'm'
    )

    private val initials: List<String> = listOf(
        "zh", "ch", "sh",
        "b", "p", "m", "f",
        "d", "t", "n", "l",
        "g", "k", "h",
        "j", "q", "x",
        "r", "z", "c", "s",
        "y", "w"
    ).sortedByDescending { it.length }

    private val finals: List<String> = listOf(
        "iang", "iong", "uang", "ueng",
        "uai", "uan", "uei", "iao", "ian",
        "ing", "ang", "eng", "ong",
        "ai", "ei", "ao", "ou", "an", "en",
        "ia", "ie", "iu", "in",
        "ua", "uo", "ui", "un",
        "üe", "üan", "ün", "ue",
        "a", "o", "e", "i", "u", "ü", "er"
    ).distinct().sortedByDescending { it.length }

    /**
     * Entrada: "wèishénme", "nǐhǎo", "xīngqī yī", "Zhōngguó"
     * Saída:   "uei-xen-me", "ni-hau", "xing-tchi i", "jong-guo"
     */
    fun toPt(pinyin: String): String {
        val raw = pinyin.trim()
        if (raw.isBlank()) return ""

        return raw
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                val base = stripTones(word).lowercase()
                val syllables = segmentWord(base)
                syllables.joinToString("-") { syl -> syllableToPt(syl) }
            }
    }

    private fun stripTones(s: String): String {
        val out = StringBuilder(s.length)
        for (ch in s) {
            val mapped = toneStripMap[ch]
            out.append(mapped ?: ch)
        }
        return out.toString()
    }

    private fun segmentWord(word: String): List<String> {
        val w = word
        val result = mutableListOf<String>()
        var i = 0

        while (i < w.length) {
            val ch = w[i]

            // ignora símbolos/traços etc (mantém avanço)
            if (!ch.isLetter() && ch != 'ü') {
                i += 1
                continue
            }

            var matched = false

            // tenta com inicial explícita
            for (ini in initials) {
                if (w.startsWith(ini, startIndex = i)) {
                    val j = i + ini.length
                    for (fin in finals) {
                        if (w.startsWith(fin, startIndex = j)) {
                            result.add(w.substring(i, j + fin.length))
                            i = j + fin.length
                            matched = true
                            break
                        }
                    }
                    if (matched) break
                }
            }

            if (matched) continue

            // tenta sem inicial (sílabas começando por vogal)
            for (fin in finals) {
                if (w.startsWith(fin, startIndex = i)) {
                    result.add(w.substring(i, i + fin.length))
                    i += fin.length
                    matched = true
                    break
                }
            }

            if (matched) continue

            // fallback: consome 1 char para evitar loop infinito
            result.add(w.substring(i, i + 1))
            i += 1
        }

        return result
    }

    private fun syllableToPt(syl: String): String {
        val s = syl
        var ini = ""
        var fin = s

        for (cand in initials) {
            if (s.startsWith(cand)) {
                ini = cand
                fin = s.substring(cand.length)
                break
            }
        }

        val finPtRaw = fin.replace("ü", "iu")

        // finais contraídos em pinyin: ui=uei, iu=iou
        val finPt = when (finPtRaw) {
            "ui" -> "uei"
            "iu" -> "iou"
            "ao" -> "au"
            else -> finPtRaw
        }

        val iniPt = when (ini) {
            "zh" -> "j"
            "ch" -> "tch"
            "sh" -> "x"
            "r" -> "j"
            "q" -> "tch"
            "j" -> "dj"
            "c" -> "ts"
            "z" -> "dz"

            // glides: evita duplicação (yi -> i, wu -> u)
            "y" -> if (finPt.startsWith("i")) "" else "i"
            "w" -> if (finPt.startsWith("u")) "" else "u"
            else -> ini
        }

        return iniPt + finPt
    }
}
