package com.keylingo.app.data.language.spanish

import java.text.Normalizer

/**
 * Low-level Spanish orthography helpers: accent folding, and the stress
 * rules needed when a clitic pronoun is attached to a verb form
 * ("llama" + "me" -> "llámame", "comiendo" + "lo" -> "comiéndolo").
 */
object SpanishText {

    private val combiningMarks = Regex("\\p{Mn}+")

    /**
     * Removes acute accents and diaeresis for accent-insensitive matching,
     * but keeps ñ distinct from n (they are different letters in Spanish:
     * "año" and "ano" are different words). Lowercases as well.
     */
    fun fold(text: String): String {
        // Compose first so "n" + combining tilde is treated as the letter ñ.
        val lower = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFC)
        val sb = StringBuilder(lower.length)
        for (ch in lower) {
            when {
                ch == 'ñ' -> sb.append('ñ')
                ch.code < 0x80 -> sb.append(ch)
                // Decompose one character at a time and drop its marks: á -> a, ü -> u.
                else -> sb.append(combiningMarks.replace(Normalizer.normalize(ch.toString(), Normalizer.Form.NFD), ""))
            }
        }
        return sb.toString()
    }

    /** Folds ñ to n too -- used only for typo-tolerant matching of what the user typed on a plain QWERTY layout. */
    fun foldIncludingEnye(text: String): String = fold(text).replace('ñ', 'n')

    private const val STRONG = "aeoáéó"
    private const val WEAK = "iuü"
    private const val ACCENTED = "áéíóú"

    private fun isVowel(c: Char) = c in "aeiouáéíóúü"

    private fun addAccent(c: Char): Char = when (c) {
        'a' -> 'á'; 'e' -> 'é'; 'i' -> 'í'; 'o' -> 'ó'; 'u' -> 'ú'; else -> c
    }

    private fun removeAccent(c: Char): Char = when (c) {
        'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; else -> c
    }

    /**
     * Vowel nuclei of [word] as index ranges, treating diphthongs (weak +
     * strong, strong + weak, weak + weak) as one nucleus unless the weak
     * vowel carries a written accent (hiatus, e.g. "día").
     */
    private fun nuclei(word: String): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var i = 0
        while (i < word.length) {
            if (!isVowel(word[i])) { i++; continue }
            var end = i
            while (end + 1 < word.length && isVowel(word[end + 1])) {
                val a = word[end]; val b = word[end + 1]
                val hiatus = (a in STRONG && b in STRONG) || a == 'í' || a == 'ú' || b == 'í' || b == 'ú'
                if (hiatus) break
                end++
            }
            // "qu" / "gu" + e/i: the u is silent, not part of the nucleus.
            val start = if (word[i] == 'u' && i > 0 && (word[i - 1] == 'q' || word[i - 1] == 'g') && end > i) i + 1 else i
            result.add(start..end)
            i = end + 1
        }
        return result
    }

    /** Index (into [nuclei]) of the stressed nucleus of [word], following written accents or the default rules. */
    private fun stressedNucleus(word: String, nuclei: List<IntRange>): Int {
        nuclei.forEachIndexed { index, range ->
            if (range.any { word[it] in ACCENTED }) return index
        }
        if (nuclei.size <= 1) return 0
        val last = word.last()
        return if (isVowel(last) || last == 'n' || last == 's') nuclei.size - 2 else nuclei.size - 1
    }

    /**
     * Appends unstressed [suffix] (clitics such as "me", "lo", "se", "nos")
     * to [base], adding or removing a written accent so the stress stays on
     * the same syllable it had in [base]. Deterministic implementation of
     * the standard RAE accentuation rules for the cases clitic attachment
     * produces.
     */
    fun attachUnstressed(base: String, suffix: String): String {
        val baseNuclei = nuclei(base)
        if (baseNuclei.isEmpty()) return base + suffix
        val stressIndex = stressedNucleus(base, baseNuclei)

        // Syllable structure is computed WITH the base's accent, so a hiatus
        // such as "pa-ís" stays two syllables after the suffix is added.
        val combinedAccented = base + suffix
        val combinedNuclei = nuclei(combinedAccented)
        val stressedRange = combinedNuclei[stressIndex]
        val chars = combinedAccented.toCharArray()
        for (i in chars.indices) chars[i] = removeAccent(chars[i])

        // A stressed weak vowel next to a strong one (hiatus) always keeps its accent: países, día, oír.
        val baseVowelIndex = baseNuclei[stressIndex].first
        val weakInHiatus = stressedRange.first == stressedRange.last &&
            base.getOrNull(baseVowelIndex).let { it == 'í' || it == 'ú' } &&
            (base.getOrNull(baseVowelIndex - 1)?.let { it in STRONG } == true ||
                combinedAccented.getOrNull(baseVowelIndex + 1)?.let { it in STRONG } == true)
        val unaccented = String(chars)
        val defaultStress = defaultStressIndex(unaccented, combinedNuclei.size)
        if (!weakInHiatus && defaultStress == stressIndex) return unaccented

        val target = if (weakInHiatus) stressedRange.first
        else stressedRange.firstOrNull { chars[it] in STRONG } ?: stressedRange.last
        chars[target] = addAccent(chars[target])
        return String(chars)
    }

    private fun defaultStressIndex(unaccentedWord: String, nucleusCount: Int): Int {
        if (nucleusCount <= 1) return 0
        val last = unaccentedWord.last()
        return if (isVowel(last) || last == 'n' || last == 's') nucleusCount - 2 else nucleusCount - 1
    }

    /** "y" becomes "e" before an /i/ sound, "o" becomes "u" before an /o/ sound. */
    fun conjunctionAnd(nextWord: String?): String {
        val n = nextWord?.lowercase() ?: return "y"
        return if ((n.startsWith("i") || n.startsWith("hi")) && !n.startsWith("hie") && !n.startsWith("ia") && !n.startsWith("io")) "e" else "y"
    }

    fun conjunctionOr(nextWord: String?): String {
        val n = nextWord?.lowercase() ?: return "o"
        return if (n.startsWith("o") || n.startsWith("ho")) "u" else "o"
    }

    fun capitalizeFirst(text: String): String =
        if (text.isEmpty()) text else text.replaceFirstChar { it.titlecase() }
}
