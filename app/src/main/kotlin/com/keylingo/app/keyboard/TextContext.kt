package com.keylingo.app.keyboard

import java.text.BreakIterator

/**
 * Pure analysis of the text immediately around the cursor. Everything is
 * computed from [before] (text before the cursor, a bounded window) and
 * [after] (a few characters after it), never from the whole field: the
 * cursor may be anywhere, and long documents must not be copied over IPC
 * on every keystroke.
 */
data class TextContext(val before: String, val after: String = "") {

    /** The partial word ending at the cursor: letters (any script, incl. á/ñ), apostrophes and inner hyphens. */
    val currentWord: String by lazy {
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        before.substring(i).trimStart('\'', '’', '-')
    }

    /** True when the cursor sits inside a word (letters continue after it) -- completions then must not autocorrect. */
    val cursorInsideWord: Boolean get() = after.isNotEmpty() && isWordChar(after[0])

    /** Completed words before [currentWord], most recent last, within the current sentence. */
    val previousWords: List<String> by lazy {
        val head = before.dropLast(currentWord.length)
        val sentence = head.substring(lastSentenceBoundary(head))
        wordRegex.findAll(sentence).map { it.value.trim('\'', '’', '-') }.filter { it.isNotEmpty() }.toList()
    }

    val previousWord: String? get() = previousWords.lastOrNull()
    val secondPreviousWord: String? get() = previousWords.getOrNull(previousWords.size - 2)

    /** Like [currentSentence] but keeping trailing whitespace/punctuation, so callers can tell whether the last word is finished. */
    val currentSentenceRaw: String by lazy { before.substring(lastSentenceBoundary(before)).trimStart() }

    /** The sentence being typed (text after the last . ! ? or newline), used for hints and translation. */
    val currentSentence: String by lazy { before.substring(lastSentenceBoundary(before)).trim() }

    /** Should the next letter be capitalised automatically? Start of field, after a newline, or after . ! ? plus space. */
    val atSentenceStart: Boolean by lazy {
        if (before.isEmpty()) return@lazy true
        if (before.last() == '\n') return@lazy true
        val trimmed = before.trimEnd(' ', ' ')
        if (trimmed.isEmpty()) return@lazy true
        if (trimmed.length == before.length) return@lazy false // directly after a character, not a space
        trimmed.last() in ".!?¡¿" || trimmed.last() == '\n'
    }

    /** Characters (a whole grapheme cluster: emoji, accented letters) the next Backspace should delete. */
    val lastGraphemeLength: Int by lazy { lastGraphemeLength(before) }

    companion object {
        private val wordRegex = Regex("[\\p{L}\\p{M}'’-]+")

        /**
         * One whitespace-free token that is a web address or e-mail: a scheme URL, `www.` host,
         * `name@host.tld`, or `host.tld` with a common top-level domain. Matched on lower-cased
         * text; no inline flags (Android's ICU regex engine rejects some, see SpanishGrammarNotes).
         */
        private val addressToken = Regex(
            """[a-z][a-z0-9+.-]*://\S*|www\.\S+|[^\s@]+@[^\s@]+\.[^\s@]+|(?:[\p{L}\p{Nd}-]+\.)+(?:com|org|net|edu|gov|io|co|es|mx|ar|cl|pe|uk|de|fr|it|app|dev|ai|info|me|tv|us|ca|in|ly|be)(?:[/:?#]\S*)?"""
        )
        private val trailingPunctuation = Regex("[.,!?;:)\\]]+$")

        /**
         * True if [text] contains a web address or e-mail ("www.youtube.com", "maria@example.com").
         * Translation and grammar help are skipped for such text, e.g. a URL typed into a browser
         * address bar, which is otherwise an ordinary assisted field.
         */
        fun containsWebAddressOrEmail(text: String): Boolean =
            text.lowercase().split(' ', '\t', '\n').any { token ->
                val t = token.replace(trailingPunctuation, "")
                t.length >= 4 && addressToken.matches(t)
            }

        fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\'' || c == '’' || Character.getType(c) == Character.NON_SPACING_MARK.toInt() ||
            c == '-'

        fun lastSentenceBoundary(text: String): Int {
            for (i in text.length - 1 downTo 0) {
                val c = text[i]
                if (c == '\n' || ((c == '.' || c == '!' || c == '?') && (i == text.length - 1 || text[i + 1].isWhitespace()))) return i + 1
            }
            return 0
        }

        /** Length in UTF-16 chars of the last user-perceived character, so Backspace never splits a surrogate pair or emoji sequence. */
        fun lastGraphemeLength(text: String): Int {
            if (text.isEmpty()) return 0
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(text)
            var start = iterator.preceding(text.length).coerceAtLeast(0)
            // Older break-iterator data splits ZWJ emoji sequences (👩‍💻): keep extending while the cluster is joined by a ZWJ.
            while (start > 1 && text[start - 1] == '\u200D') {
                start = iterator.preceding(start - 1).coerceAtLeast(0)
            }
            return (text.length - start).coerceAtLeast(1)
        }
    }
}
