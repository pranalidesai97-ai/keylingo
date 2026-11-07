package com.keylingo.app.compat

import com.keylingo.app.data.hint.SpanishGrammarNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer

/**
 * Guards against regex syntax that compiles on a desktop JVM but not on
 * Android, where java.util.regex is backed by ICU4C. Unit tests run on the
 * JVM, so they could not see the `(?U)` pattern that crashed
 * KeyLingoInputMethodService on a real phone (PatternSyntaxException near
 * index 3, thrown while Hilt constructed LearningAssistant).
 *
 * 1. Statically rejects constructs ICU does not support, in every production
 *    pattern: [SpanishGrammarNotes.RULES] plus every `Regex("...")` literal
 *    under app/src/main/kotlin.
 * 2. Writes `build/android-regex-cases.tsv`: every pattern to compile, plus
 *    find-cases with the JVM's results. `tools/android-regex-check/run.sh`
 *    replays the file in ICU4C (Android's engine) and fails if any pattern
 *    does not compile or any result differs.
 */
class AndroidRegexCompatibilityTest {

    private val moduleDir = listOf(File("."), File("app")).first { File(it, "src/main/kotlin").isDirectory }
    private val sourceRoot = File(moduleDir, "src/main/kotlin")

    data class Pattern(val source: String, val ignoreCase: Boolean, val origin: String)

    // ---------------------------------------------------------------- static rules

    /** Why [pattern] would fail on Android's ICU regex engine, or null if it uses only supported syntax. */
    private fun androidIncompatibility(pattern: String): String? {
        // Inline flag groups: ICU supports only i, m, s, x, w (and '-' to turn them off).
        Regex("""\(\?([A-Za-z-]+)[:)]""").findAll(pattern).forEach { m ->
            val bad = m.groupValues[1].filter { it !in "imsxw-" }
            if (bad.isNotEmpty()) return "inline flag(s) '$bad' in '${m.value}' are not supported by ICU (Android)"
        }
        // Java-only character-class names.
        Regex("""\\[pP]\{(java\w+|Is\w+|In\w+)}""").find(pattern)?.let { return "Java-only property '${it.value}'" }
        // ICU needs a bounded look-behind.
        Regex("""\(\?<[=!]((?:[^()\\]|\\.)*)\)""").findAll(pattern).forEach { m ->
            val body = m.groupValues[1].replace(Regex("""\\.|\[[^\]]*]"""), "x")
            if (body.contains('*') || body.contains('+') || Regex("""\{\d+,}""").containsMatchIn(body)) return "unbounded look-behind '${m.value}'"
        }
        return null
    }

    @Test fun theCheckerRejectsTheExactPatternThatCrashedTheKeyboard() {
        val crashed = "(?U)\\b(estoy|estás|esta|está|estamos|están|estaba|estabas|estábamos|estaban)\\s+\\p{L}+(ando|iendo|yendo)\\b"
        assertNotNull(androidIncompatibility(crashed))
        assertNotNull(androidIncompatibility("(?U)abc"))
        assertNotNull(androidIncompatibility("(?d)a"))
        assertNotNull(androidIncompatibility("\\p{javaLowerCase}"))
        assertNotNull(androidIncompatibility("(?<!\\p{L}+)a"))
        assertNull(androidIncompatibility("(?i)abc"))
        assertNull(androidIncompatibility("(?:ab)+"))
        assertNull(androidIncompatibility("(?<![\\p{L}\\p{M}\\p{Nd}])esta(?![\\p{L}])"))
    }

    // ---------------------------------------------------------------- collecting production patterns

    /** Decodes a Kotlin string literal body; returns null if it contains templates (cannot be evaluated statically). */
    private fun decodeKotlinLiteral(body: String, raw: Boolean): String? {
        if (raw) return if (Regex("""\$[{A-Za-z_]""").containsMatchIn(body)) null else body
        val sb = StringBuilder(); var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '$' && i + 1 < body.length && (body[i + 1] == '{' || body[i + 1].isLetter() || body[i + 1] == '_')) return null
            if (c == '\\' && i + 1 < body.length) {
                when (val n = body[i + 1]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b')
                    'u' -> { sb.append(body.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                    else -> sb.append(n) // \\ \" \' \$
                }
                i += 2; continue
            }
            sb.append(c); i++
        }
        return sb.toString()
    }

    private fun sourceLiteralPatterns(): List<Pattern> {
        val found = mutableListOf<Pattern>()
        val call = Regex("""Regex\(\s*(?:"{3}([\s\S]*?)"{3}|"((?:[^"\\]|\\.)*)")\s*(,\s*RegexOption\.IGNORE_CASE)?""")
        sourceRoot.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            call.findAll(text).forEach { m ->
                val raw = m.groups[1] != null
                val decoded = decodeKotlinLiteral(if (raw) m.groupValues[1] else m.groupValues[2], raw)
                if (decoded != null) {
                    val line = text.substring(0, m.range.first).count { it == '\n' } + 1
                    found += Pattern(decoded, m.groups[3] != null, "${file.relativeTo(sourceRoot)}:$line")
                }
            }
        }
        return found
    }

    private fun allProductionPatterns(): List<Pattern> =
        SpanishGrammarNotes.RULES.mapIndexed { i, r -> Pattern(r.pattern, false, "SpanishGrammarNotes.RULES[$i]") } + sourceLiteralPatterns()

    @Test fun everyProductionPatternUsesOnlyAndroidSupportedSyntax() {
        val patterns = allProductionPatterns()
        // The scan must actually find the codebase's patterns (guards against a broken scanner passing vacuously).
        assertTrue("only ${patterns.size} patterns found", patterns.size >= 25)
        assertTrue("scanner missed the IGNORE_CASE pattern", patterns.any { it.origin.contains("data/language/TypingLanguageDetector.kt") && it.ignoreCase })
        val problems = patterns.mapNotNull { p -> androidIncompatibility(p.source)?.let { "${p.origin}: $it\n    ${p.source}" } }
        assertTrue("Android-incompatible regex:\n" + problems.joinToString("\n"), problems.isEmpty())
        // Java-only flags passed as constants rather than inline.
        val flagUses = sourceRoot.walkTopDown().filter { it.extension == "kt" }
            .flatMap { f ->
                f.readLines().mapIndexedNotNull { i, l ->
                    val code = l.substringBefore("//").trimStart()
                    val isComment = code.startsWith("*") || code.startsWith("/*")
                    if (!isComment && Regex("UNICODE_CHARACTER_CLASS|CANON_EQ").containsMatchIn(code)) "${f.name}:${i + 1}" else null
                }
            }
            .toList()
        assertEquals(emptyList<String>(), flagUses)
        // And all of them compile here too.
        patterns.forEach { p -> if (p.ignoreCase) Regex(p.source, RegexOption.IGNORE_CASE) else Regex(p.source) }
    }

    @Test fun writesCasesForTheIcuCheck() {
        val patterns = allProductionPatterns()
        val inputs = listOf(
            "estoy trabajando", "estás comiendo", "estamos viviendo", "estaba estudiando", "Estábamos durmiendo cuando llamaste",
            "¿Estás leyendo?", "ESTOY TRABAJANDO", "esta\u0301s comiendo", "Estoy trabajando.", "¡Estoy trabajando!",
            "detesta trabajando", "manifestamos cantando", "estoy cansado", "estoy andandox",
            "Íbamos a comer", "Voy a llamar a mi mamá", "Me gustan los perros", "Tengo que trabajar", "Tengo 20 años",
            "Hoy hace frío", "Acabo de comer", "Ayer fui al mercado", "He comido", "No entiendo",
            "", "?!...", "I am working at the station today.", "This is interesting",
        )
        val out = StringBuilder("# Generated by AndroidRegexCompatibilityTest; replay with tools/android-regex-check/run.sh\n")
        patterns.forEach { p ->
            require('\t' !in p.source && '\n' !in p.source) { "pattern not TSV-safe: ${p.origin}" }
            out.append("COMPILE\t").append(if (p.ignoreCase) "i" else "").append('\t').append(p.source).append('\n')
        }
        // Find-cases for the note rules, on exactly the text notesFor() matches (NFC, lower case), with the JVM's answer.
        SpanishGrammarNotes.RULES.forEach { rule ->
            val regex = Regex(rule.pattern)
            inputs.forEach { input ->
                val text = Normalizer.normalize(input, Normalizer.Form.NFC).lowercase()
                out.append("FIND\t\t").append(rule.pattern).append('\t').append(text).append('\t')
                    .append(if (regex.containsMatchIn(text)) "1" else "0").append('\n')
            }
        }
        // Case-insensitive Spanish-character check (TypingLanguageDetector) on capitals.
        patterns.filter { it.ignoreCase }.forEach { p ->
            listOf("ÁRBOL", "Niño", "hello").forEach { t ->
                out.append("FIND\ti\t").append(p.source).append('\t').append(t).append('\t')
                    .append(if (Regex(p.source, RegexOption.IGNORE_CASE).containsMatchIn(t)) "1" else "0").append('\n')
            }
        }
        val file = File(moduleDir, "build/android-regex-cases.tsv").apply { parentFile.mkdirs() }
        file.writeText(out.toString(), Charsets.UTF_8)
        println("ANDROID_REGEX_CASES=${file.absolutePath} patterns=${patterns.size}")
    }
}
