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
class Lexicon(
    words: Collection<LexiconWord>,
    private val foldAccents: Boolean,
    /** Spanish: sound-alike spellings (s/c/z, b/v, silent h) are cheap typos, see [TypoDistance]. */
    private val spanishPhonetics: Boolean = false,
) {

    private val sorted: Array<LexiconWord>
    private val keys: Array<String>
    private val exact: Map<String, LexiconWord>
    /** Suggestable words keyed by (first letter, length): whole-word typo candidates are only ever within +-2 letters. */
    private val byLetterAndLength: Map<Long, IntArray>
    /** Common suggestable words (rank <= [MAX_COMPLETION_RANK]) per first letter, for typo'd completions. */
    private val completionsByLetter: Map<Char, IntArray>

    init {
        val dedup = LinkedHashMap<String, LexiconWord>()
        for (w in words) {
            val k = w.display.lowercase()
            val existing = dedup[k]
            if (existing == null || w.rank < existing.rank) dedup[k] = w
        }
        // Load-time hot path (runs once per process, ~110k words): a primitive comparator and hand-built
        // index arrays instead of compareBy/groupBy, which box every rank and index.
        val pairs = dedup.values.map { matchKey(it.display) to it }.toTypedArray()
        java.util.Arrays.sort(pairs) { a, b ->
            val c = a.first.compareTo(b.first)
            if (c != 0) c else Integer.compare(a.second.rank, b.second.rank)
        }
        keys = Array(pairs.size) { pairs[it].first }
        sorted = Array(pairs.size) { pairs[it].second }
        exact = dedup
        byLetterAndLength = index { i -> if (sorted[i].suggestable && keys[i].length >= 2) bucket(keys[i][0], keys[i].length) else null }
        completionsByLetter = index { i ->
            if (sorted[i].suggestable && keys[i].length >= 2 && sorted[i].rank <= MAX_COMPLETION_RANK) keys[i][0] else null
        }
    }

    /** Groups word indices (in key order) by [keyOf]; null leaves a word out. */
    private inline fun <K : Any> index(keyOf: (Int) -> K?): Map<K, IntArray> {
        val counts = HashMap<K, IntArray>() // [count, fill position]
        for (i in keys.indices) keyOf(i)?.let { counts.getOrPut(it) { IntArray(2) }[0]++ }
        val out = HashMap<K, IntArray>(counts.size * 2)
        for ((k, c) in counts) out[k] = IntArray(c[0])
        for (i in keys.indices) keyOf(i)?.let { k -> val c = counts.getValue(k); out.getValue(k)[c[1]++] = i }
        return out
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
            key.length <= 2 -> 0.6 // one slip: replacing both letters of a two-letter word ("xq" -> "da") is not a typo
            key.length <= 3 -> 0.95
            key.length <= 6 -> 1.45
            else -> 1.7
        }
        // Short words: the first letter may be a neighbouring-key slip. Long words: first-letter slips are
        // rare and expensive to search, so only the typed first letter (or a 1st/2nd-letter swap) is scanned.
        val firstLetters = buildSet {
            if (key.length <= 6) addAll(TypoDistance.neighbourhood(key[0]))
            add(key[0]); add(key[1])
            if (spanishPhonetics) addAll(TypoDistance.soundAlikes(key[0])) // "sena" -> cena, "vien" -> bien
        }
        // Spanish silent h ("ola" -> hola, "aber" -> haber): only words "h" + what was typed, so that bucket is filtered tightly.
        val silentH = spanishPhonetics && 'h' !in firstLetters
        val out = ArrayList<Correction>()
        val scratch = TypoDistance.Scratch()
        val result = TypoDistance.ScanResult()
        val wantPrefix = includeCompletions && key.length >= 3
        // Second-letter filter: a plausible typo keeps the 2nd letter, hits a neighbouring key, transposes it with
        // the 1st/3rd letter, or drops/doubles a letter (then the candidate's 3rd letter matches the typed 2nd).
        val secondLetters: Set<Char> = buildSet {
            addAll(TypoDistance.neighbourhood(key[1])); add(key[1]); add(key[0]); key.getOrNull(2)?.let { add(it) }
            if (spanishPhonetics) addAll(TypoDistance.soundAlikes(key[1])) // "nesecito"; "aora" -> ahora passes the dropped-letter rule
        }
        fun consider(idx: Int, allowPrefix: Boolean) {
            val w = sorted[idx]
            if (w.rank > maxCandidateRank) return
            val cand = keys[idx]
            if (cand == key) return
            if (key.length >= 3 && cand[1] !in secondLetters && !(cand.length > 2 && cand[2] == key[1])) return
            val tryPrefix = allowPrefix && cand.length > key.length && w.rank <= MAX_COMPLETION_RANK
            TypoDistance.scan(key, cand, maxCost, tryPrefix, scratch, result, spanishPhonetics)
            var dist = result.whole
            var prefix = false
            if (tryPrefix && result.prefix + 0.15 < dist && result.prefix <= maxCost - 0.4) { dist = result.prefix + 0.15; prefix = true }
            if (dist <= maxCost) out.add(Correction(w, dist, prefix))
        }
        for (letter in firstLetters) {
            // Completions with a typo are only plausible when the first letter itself was typed right.
            val prefixHere = wantPrefix && letter == key[0]
            for (len in (key.length - 2).coerceAtLeast(2)..(key.length + 2)) {
                byLetterAndLength[bucket(letter, len)]?.forEach { consider(it, prefixHere) }
            }
        }
        if (silentH) {
            for (len in key.length + 1..key.length + 2) byLetterAndLength[bucket('h', len)]?.forEach { if (keys[it][1] == key[0]) consider(it, false) }
        }
        if (wantPrefix) {
            // Longer words (beyond the +-2 window) can only be typo'd completions of what was typed so far.
            completionsByLetter[key[0]]?.forEach { idx -> if (keys[idx].length > key.length + 2) consider(idx, true) }
        }
        return out.sortedWith(compareBy({ score(it) }, { it.word.rank })).take(limit)
    }

    /** Combined closeness + frequency: one edit is worth roughly a 100x frequency difference. */
    fun score(c: Correction): Double = c.distance * 4.0 + kotlin.math.ln((c.word.rank + 10).toDouble())

    private fun bucket(letter: Char, length: Int): Long = (letter.code.toLong() shl 16) or length.toLong()

    companion object {
        /** Typo-tolerant completion is limited to reasonably common words (cost control; rare words still match exactly). */
        const val MAX_COMPLETION_RANK = 30_000

        /** Parses the bundled TSV format: word, rank, flags ("n" = known but never suggested). */
        fun parseTsv(lines: Sequence<String>, foldAccents: Boolean): Lexicon = Lexicon(parseWords(lines), foldAccents)

        /** `word<TAB>rank<TAB>flags` lines (flag n = known, never suggested); `#` comments ignored. */
        fun parseWords(lines: Sequence<String>): List<LexiconWord> {
            val words = ArrayList<LexiconWord>(70_000)
            for (line in lines) {
                if (line.isEmpty() || line.startsWith("#")) continue
                val tab1 = line.indexOf('\t'); if (tab1 <= 0) continue
                val tab2 = line.indexOf('\t', tab1 + 1)
                val rank = (if (tab2 < 0) line.substring(tab1 + 1) else line.substring(tab1 + 1, tab2)).toIntOrNull() ?: continue
                val suggestable = tab2 < 0 || line.indexOf('n', tab2 + 1) < 0
                words.add(LexiconWord(line.substring(0, tab1), rank, suggestable))
            }
            return words
        }
    }
}
