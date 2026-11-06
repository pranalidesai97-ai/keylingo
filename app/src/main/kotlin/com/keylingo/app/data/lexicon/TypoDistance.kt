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

    /**
     * Distance between [typed] and [candidate] (both lowercase). Returns a
     * value > [maxCost] (not necessarily exact) once it is clear the true
     * distance exceeds the bound.
     */
    fun distance(typed: String, candidate: String, maxCost: Double = 2.0): Double {
        val n = typed.length; val m = candidate.length
        if (abs(n - m) > maxCost / 0.35 + 1) return maxCost + 1
        // Three rolling rows for the transposition lookback.
        var prev2 = DoubleArray(m + 1)
        var prev = DoubleArray(m + 1) { it * 0.9 }
        var cur = DoubleArray(m + 1)
        for (i in 1..n) {
            cur[0] = i * 0.9
            var rowMin = cur[0]
            val tc = typed[i - 1]
            for (j in 1..m) {
                val cc = candidate[j - 1]
                // Deleting a typed char that duplicates its neighbour ("helllo") is cheap.
                val deletionCost = if (i >= 2 && typed[i - 2] == tc) 0.35 else 0.9
                // Inserting a char the user failed to double ("adress") is cheap.
                val insertionCost = if (j >= 2 && candidate[j - 2] == cc) 0.35 else 0.9
                var best = min(
                    prev[j] + deletionCost,
                    min(cur[j - 1] + insertionCost, prev[j - 1] + substitutionCost(tc, cc))
                )
                if (i >= 2 && j >= 2 && tc == candidate[j - 2] && typed[i - 2] == cc && tc != cc) {
                    best = min(best, prev2[j - 2] + 0.55)
                }
                cur[j] = best
                if (best < rowMin) rowMin = best
            }
            if (rowMin > maxCost) return maxCost + 1
            val tmp = prev2; prev2 = prev; prev = cur; cur = tmp
        }
        return prev[m]
    }

    /**
     * Best distance between [typed] and any prefix of [candidate] whose
     * length is within one character of [typed]'s -- i.e. "how far is what
     * was typed from the beginning of this word", for completing a word
     * whose already-typed prefix contains a typo ("recie" -> "receive").
     */
    fun prefixDistance(typed: String, candidate: String, maxCost: Double = 1.5): Double {
        var best = maxCost + 1
        for (len in (typed.length - 1)..(typed.length + 1)) {
            if (len < 1 || len > candidate.length) continue
            val d = distance(typed, candidate.substring(0, len), maxCost)
            if (d < best) best = d
        }
        return best
    }
}
