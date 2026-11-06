package com.keylingo.app.data.lexicon

import kotlin.math.abs
import kotlin.math.min

/**
 * Keyboard-aware, weighted, restricted Damerau-Levenshtein distance used for
 * typo tolerance. Costs reflect how people actually mistype on a QWERTY
 * touch keyboard:
 *
 * - substituting a physically adjacent key ("tge" for "the")  0.45
 * - substituting any other key                                 1.0
 * - transposing two neighbouring letters ("teh")               0.55
 * - an accidentally doubled / dropped repeated letter          0.35
 * - any other insertion or deletion                            0.9
 * - accent-only differences (e vs é, n vs ñ)                   0.05
 *
 * Deterministic, no ML. The computation is bounded by [maxCost] and
 * abandons a row as soon as every cell exceeds it, so scanning thousands of
 * candidates per keystroke stays cheap.
 */
object TypoDistance {

    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val rowOffsets = floatArrayOf(0f, 0.5f, 1.5f)

    private val keyPositions: Map<Char, Pair<Float, Float>> = buildMap {
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch -> put(ch, Pair(c + rowOffsets[r], r.toFloat())) }
        }
    }

    private fun baseLetter(c: Char): Char = when (c) {
        'á', 'à', 'ä', 'â' -> 'a'; 'é', 'è', 'ë', 'ê' -> 'e'; 'í', 'ì', 'ï', 'î' -> 'i'
        'ó', 'ò', 'ö', 'ô' -> 'o'; 'ú', 'ù', 'ü', 'û' -> 'u'; 'ñ' -> 'n'; 'ç' -> 'c'
        else -> c
    }

    fun areAdjacentKeys(a: Char, b: Char): Boolean {
        val pa = keyPositions[baseLetter(a)] ?: return false
        val pb = keyPositions[baseLetter(b)] ?: return false
        val dx = abs(pa.first - pb.first); val dy = abs(pa.second - pb.second)
        return dy <= 1f && dx <= 1.01f && !(dx == 0f && dy == 0f)
    }

    /** Lowercase letters adjacent to [c] on the QWERTY layout, including [c] itself. */
    fun neighbourhood(c: Char): Set<Char> = keyPositions.keys.filter { it == baseLetter(c) || areAdjacentKeys(it, c) }.toSet()

    private fun substitutionCost(a: Char, b: Char): Double = when {
        a == b -> 0.0
        baseLetter(a) == baseLetter(b) -> 0.05
        areAdjacentKeys(a, b) -> 0.45
        else -> 1.0
    }

    /** Result of one [scan]: distance to the whole candidate, and to its best-matching prefix. */
    class ScanResult {
        var whole = Double.MAX_VALUE
        var prefix = Double.MAX_VALUE
    }

    /** Reusable DP rows, so scanning thousands of candidates per keystroke allocates nothing. */
    class Scratch(capacity: Int = 64) {
        var r0 = DoubleArray(capacity); var r1 = DoubleArray(capacity); var r2 = DoubleArray(capacity)
        fun ensure(n: Int) {
            if (r0.size < n) { r0 = DoubleArray(n); r1 = DoubleArray(n); r2 = DoubleArray(n) }
        }
    }

    /**
     * One dynamic-programming pass over [candidate] that yields both the
     * distance to the whole word (when its length is within 2 of [typed]'s)
     * and -- if [wantPrefix] -- the distance to the closest prefix whose
     * length is within one of [typed]'s ("recie" vs "receive"). The last DP
     * row holds the distance to every candidate prefix, so no extra passes
     * or substrings are needed. Rows are abandoned once every cell exceeds
     * [maxCost].
     */
    fun scan(typed: String, candidate: String, maxCost: Double, wantPrefix: Boolean, scratch: Scratch, out: ScanResult) {
        out.whole = Double.MAX_VALUE; out.prefix = Double.MAX_VALUE
        val n = typed.length
        val wantWhole = abs(candidate.length - n) <= 2
        if (!wantWhole && !wantPrefix) return
        // Columns needed: the whole word, or just past the longest useful prefix.
        val m = if (wantWhole) candidate.length else minOf(candidate.length, n + 1)
        scratch.ensure(m + 1)
        var prev2 = scratch.r0; var prev = scratch.r1; var cur = scratch.r2
        for (j in 0..m) prev[j] = j * 0.9
        for (i in 1..n) {
            cur[0] = i * 0.9
            var rowMin = cur[0]
            val tc = typed[i - 1]
            for (j in 1..m) {
                val cc = candidate[j - 1]
                val deletion = if (i >= 2 && typed[i - 2] == tc) 0.35 else 0.9
                val insertion = if (j >= 2 && candidate[j - 2] == cc) 0.35 else 0.9
                var best = min(prev[j] + deletion, min(cur[j - 1] + insertion, prev[j - 1] + substitutionCost(tc, cc)))
                if (i >= 2 && j >= 2 && tc == candidate[j - 2] && typed[i - 2] == cc && tc != cc) best = min(best, prev2[j - 2] + 0.55)
                cur[j] = best
                if (best < rowMin) rowMin = best
            }
            if (rowMin > maxCost) return
            val tmp = prev2; prev2 = prev; prev = cur; cur = tmp
        }
        if (wantWhole) out.whole = prev[m]
        if (wantPrefix) {
            var best = Double.MAX_VALUE
            for (len in maxOf(1, n - 1)..minOf(m, n + 1)) if (len < candidate.length && prev[len] < best) best = prev[len]
            out.prefix = best
        }
    }

    /** Convenience single-pair distance (tests, one-off checks). */
    fun distance(typed: String, candidate: String, maxCost: Double = 2.0): Double {
        val out = ScanResult()
        scan(typed, candidate, maxCost, wantPrefix = false, scratch = Scratch(), out = out)
        return if (out.whole == Double.MAX_VALUE) maxCost + 1 else out.whole
    }
}
