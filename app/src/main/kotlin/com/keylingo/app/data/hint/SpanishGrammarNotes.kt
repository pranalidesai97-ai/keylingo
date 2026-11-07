package com.keylingo.app.data.hint

import java.text.Normalizer

/**
 * Short notes on the Spanish grammar in a sentence the user typed
 * ("estoy trabajando" -> "estar + gerund: action in progress"), shown on the
 * Spanish -> English sentence chip.
 *
 * Android compatibility. On Android, java.util.regex is implemented with
 * ICU4C, which rejects some syntax a desktop JVM accepts. The inline `(?U)`
 * flag (UNICODE_CHARACTER_CLASS) these patterns used to carry made
 * `icu::RegexPattern::compile` fail with U_REGEX_RULE_SYNTAX. Because the
 * patterns were compiled in LearningAssistant's constructor, the keyboard
 * service crashed during Hilt injection. So:
 *  - word boundaries are explicit look-arounds on Unicode letters, marks and
 *    digits, instead of `\b`. Without `(?U)`, a desktop JVM's `\b` treats
 *    á/é/í/ó/ú/ñ as non-word characters while ICU's does not; the look-arounds
 *    behave the same in both engines;
 *  - no inline flags (lower-casing the input replaces case-insensitivity);
 *  - patterns compile lazily on first use (a background dispatcher, never
 *    service creation). A pattern the platform rejects is skipped, not fatal.
 * AndroidRegexCompatibilityTest and tools/android-regex-check verify these
 * exact strings with ICU.
 */
internal object SpanishGrammarNotes {

    data class Rule(val pattern: String, val note: String)

    /** Not preceded by a letter, combining mark or digit (start of a word). */
    private const val START = """(?<![\p{L}\p{M}\p{Nd}])"""

    /** Not followed by a letter, combining mark or digit (end of a word). */
    private const val END = """(?![\p{L}\p{M}\p{Nd}])"""

    /** One word made of letters (and combining marks, for decomposed accents). */
    private const val WORD = """[\p{L}\p{M}]+"""

    val RULES: List<Rule> = listOf(
        Rule("""${START}(estoy|estás|esta|está|estamos|están|estaba|estabas|estábamos|estaban)\s+${WORD}(ando|iendo|yendo)$END""",
            "estar + gerund: action in progress (estoy aprendiendo)"),
        Rule("""${START}(voy|vas|va|vamos|van|iba|ibas|íbamos|iban)\s+a\s+${WORD}(ar|er|ir)$END""",
            "ir a + infinitive: near future (voy a llamar)"),
        Rule("""${START}(me|te|le|nos|les)\s+(gusta|gustan|gustaba|gustó|encanta|encantan)$END""",
            "gustar: the thing liked is the subject (me gusta / me gustan)"),
        Rule("""${START}(tengo|tienes|tiene|tenemos|tienen|tenía)\s+que$END""",
            "tener que + infinitive: have to"),
        Rule("""${START}(tengo|tienes|tiene|tenemos|tienen)\s+(hambre|sed|frío|frio|calor|miedo|sueño|prisa|razón|\d+\s+años)$END""",
            "Spanish uses tener for hunger, thirst, fear, age…"),
        Rule("""${START}hace\s+(frío|frio|calor|sol|viento|buen tiempo|mal tiempo)$END""",
            "Weather uses hacer: hace frío, hace calor"),
        Rule("""${START}(acabo|acabas|acaba|acabamos|acaban)\s+de$END""",
            "acabar de + infinitive: to have just done"),
        Rule("""${START}(fui|fuiste|fue|fuimos|fueron)\s+(a|al)$END""",
            "fui + a: preterite of ir (went to); fui is also the preterite of ser"),
        Rule("""${START}(he|has|ha|hemos|han)\s+${WORD}(ado|ido|to|cho)$END""",
            "haber + participle: present perfect"),
        Rule("""^\s*no\s""",
            "Negation: no goes right before the verb"),
    )

    private class Compiled(val rules: List<Pair<Regex, String>>, val rejected: List<String>)

    private val compiled: Compiled by lazy {
        val ok = mutableListOf<Pair<Regex, String>>()
        val rejected = mutableListOf<String>()
        for (rule in RULES) {
            try {
                ok += Regex(rule.pattern) to rule.note
            } catch (e: IllegalArgumentException) { // PatternSyntaxException: skip the note, never crash the keyboard
                rejected += rule.pattern
            }
        }
        Compiled(ok, rejected)
    }

    /** Patterns this runtime's regex engine refused to compile. Always empty unless a rule is broken; tests assert that. */
    val rejectedPatterns: List<String> get() = compiled.rejected

    /** Up to [max] notes for [sentence], most instructive first. */
    fun notesFor(sentence: String, max: Int = 2): List<String> {
        if (sentence.isBlank()) return emptyList()
        // NFC so a decomposed "está" matches "está"; lower case replaces a case-insensitive flag.
        val text = Normalizer.normalize(sentence, Normalizer.Form.NFC).lowercase()
        return compiled.rules.filter { it.first.containsMatchIn(text) }.map { it.second }.take(max)
    }
}
