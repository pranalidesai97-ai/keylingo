package com.keylingo.app.data.lexicon

import com.keylingo.app.data.language.spanish.SpanishText

/** One lexicon word: its display spelling (with capitals, apostrophes, accents) and frequency rank (lower = more common). */
data class LexiconWord(val display: String, val rank: Int, val suggestable: Boolean)

/** A scored typo-correction candidate. Lower [distance] = closer to what was typed. */
data class Correction(val word: LexiconWord, val distance: Double, val isPrefixMatch: Boolean)

/**
 * Immutable in-memory word list with fast prefix search and keyboard-aware
 * typo candidates. Used for both English (from the bundled SCOWL-derived
 * list) and Spanish (generated from the dictionary + conjugator).
 *
 * Words are indexed by a *match key* -- lowercase, apostrophes removed and,
 * for Spanish, accents folded -- so "dont" finds "don't", "im" finds "I'm"
 * and "tambien" finds "también". Lookups never allocate per candidate
 * beyond the result list; everything is array-backed and sorted once at
 * construction (on a background thread).
 */
class Lexicon(words: Collection<LexiconWord>, private val foldAccents: Boolean) {

    private val sorted: Array<LexiconWord>
    private val keys: Array<String>
    private val exact: Map<String, LexiconWord>
    private val byFirstLetter: Map<Char, IntArray>

    init {
        val dedup = LinkedHashMap<String, LexiconWord>()
        for (w in words) {
            val k = w.display.lowercase()
            val existing = dedup[k]
            if (existing == null || w.rank < existing.rank) dedup[k] = w
        }
        val pairs = dedup.values.map { matchKey(it.display) to it }.sortedWith(compareBy({ it.first }, { it.second.rank }))
        keys = Array(pairs.size) { pairs[it].first }
        sorted = Array(pairs.size) { pairs[it].second }
        exact = dedup
        byFirstLetter = keys.indices.groupBy { keys[it].firstOrNull() ?: ' ' }
            .mapValues { (_, list) -> list.toIntArray() }
    }

    val size: Int get() = sorted.size

    fun matchKey(word: String): String {
        val lower = word.lowercase().replace("'", "").replace("’", "")
        return if (foldAccents) SpanishText.foldIncludingEnye(lower) else lower
    }

    /** Exact (case-insensitive) lookup by spelling. */
    fun find(word: String): LexiconWord? = exact[word.lowercase().replace('’', '\'')]

    /** All words sharing [word]'s match key -- e.g. "were" and "we're", or "esta"/"está"/"ésta". */
    fun findByKey(word: String): List<LexiconWord> {
        val key = matchKey(word)
        var i = lowerBound(key)
        val out = mutableListOf<LexiconWord>()
        while (i < keys.size && keys[i] == key) { out.add(sorted[i]); i++ }
        return out
    }

    fun contains(word: String): Boolean = find(word) != null

    /** The [limit] most common suggestable words whose match key starts with [prefix]'s. */
    fun prefixMatches(prefix: String, limit: Int): List<LexiconWord> {
        val key = matchKey(prefix)
        if (key.isEmpty()) return emptyList()
        val best = ArrayList<LexiconWord>(limit + 1)
        var i = lowerBound(key)
        while (i < keys.size && keys[i].startsWith(key)) {
            val w = sorted[i]
            if (w.suggestable) insertTopK(best, w, limit)
            i++
        }
        return best
    }

    private fun insertTopK(best: MutableList<LexiconWord>, w: LexiconWord, limit: Int) {
        if (best.size == limit && best.last().rank <= w.rank) return
        var pos = best.size
        while (pos > 0 && best[pos - 1].rank > w.rank) pos--
        best.add(pos, w)
        if (best.size > limit) best.removeAt(best.size - 1)
    }

    private fun lowerBound(key: String): Int {
        var lo = 0; var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * Typo-correction candidates for [typed]: words within a small weighted
     * edit distance of the whole typed string, or (when [includeCompletions])
     * of its beginning. Only words whose first letter is the typed first
     * letter, a physically adjacent key, or the typed *second* letter (for
     * first-two-letter transpositions) are scanned -- first-letter errors are
     * rare on touch keyboards, and this cuts the scan by an order of magnitude.
     */
    fun corrections(typed: String, limit: Int, includeCompletions: Boolean, maxCandidateRank: Int = Int.MAX_VALUE): List<Correction> {
        val key = matchKey(typed)
        if (key.length < 2) return emptyList()
        val maxCost = when {
            key.length <= 3 -> 0.95
            key.length <= 6 -> 1.45
            else -> 2.0
        }
        val firstLetters = buildSet {
            addAll(TypoDistance.neighbourhood(key[0]))
            add(key[0]); add(key[1])
        }
        val out = ArrayList<Correction>()
        val scratch = TypoDistance.Scratch()
        val result = TypoDistance.ScanResult()
        val wantPrefix = includeCompletions && key.length >= 3
        // Second-letter filter: a plausible typo keeps the 2nd letter, hits a neighbouring key, transposes it with
        // the 1st/3rd letter, or drops/doubles a letter (then the candidate's 3rd letter matches the typed 2nd).
        val secondLetters: Set<Char> = buildSet {
            addAll(TypoDistance.neighbourhood(key[1])); add(key[1]); add(key[0]); key.getOrNull(2)?.let { add(it) }
        }
        for (letter in firstLetters) {
            val indices = byFirstLetter[letter] ?: continue
            // Completions with a typo are only plausible when the first letter itself was typed right.
            val prefixHere = wantPrefix && letter == key[0]
            for (idx in indices) {
                val w = sorted[idx]
                if (!w.suggestable || w.rank > maxCandidateRank) continue
                val cand = keys[idx]
                if (cand == key || cand.length < 2) continue
                if (key.length >= 3 && cand[1] !in secondLetters && !(cand.length > 2 && cand[2] == key[1])) continue
                val lenDiff = cand.length - key.length
                val tryPrefix = prefixHere && lenDiff >= 1 && w.rank <= MAX_COMPLETION_RANK
                if (lenDiff !in -2..2 && !tryPrefix) continue
                TypoDistance.scan(key, cand, maxCost, tryPrefix, scratch, result)
                var dist = result.whole
                var prefix = false
                if (tryPrefix && result.prefix + 0.15 < dist && result.prefix <= maxCost - 0.4) { dist = result.prefix + 0.15; prefix = true }
                if (dist <= maxCost) out.add(Correction(w, dist, prefix))
            }
        }
        return out.sortedWith(compareBy({ score(it) }, { it.word.rank })).take(limit)
    }

    /** Combined closeness + frequency: one edit is worth roughly a 100x frequency difference. */
    fun score(c: Correction): Double = c.distance * 4.0 + kotlin.math.ln((c.word.rank + 10).toDouble())

    companion object {
        /** Typo-tolerant completion is limited to reasonably common words (cost control; rare words still match exactly). */
        const val MAX_COMPLETION_RANK = 30_000

        /** Parses the bundled TSV format: word, rank, flags ("n" = known but never suggested). */
        fun parseTsv(lines: Sequence<String>, foldAccents: Boolean): Lexicon {
            val words = ArrayList<LexiconWord>(70_000)
            for (line in lines) {
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split('\t')
                if (parts.size < 2) continue
                val rank = parts[1].toIntOrNull() ?: continue
                val flags = parts.getOrNull(2).orEmpty()
                words.add(LexiconWord(parts[0], rank, suggestable = !flags.contains('n')))
            }
            return Lexicon(words, foldAccents)
        }
    }
}
