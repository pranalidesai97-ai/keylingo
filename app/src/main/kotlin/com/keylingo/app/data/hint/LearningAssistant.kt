package com.keylingo.app.data.hint

import com.keylingo.app.data.bundled.BundledLanguageData
import com.keylingo.app.data.bundled.BundledDataSupplier
import com.keylingo.app.domain.model.CefrLevel
import com.keylingo.app.domain.model.HintWord
import com.keylingo.app.domain.model.LearningAssistance
import com.keylingo.app.domain.model.LearningMode
import com.keylingo.app.domain.model.PartOfSpeech
import com.keylingo.app.domain.model.SentenceTranslation
import com.keylingo.app.domain.repository.HintProvider
import com.keylingo.app.data.AppDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [HintProvider] implementation. For the sentence being typed it:
 * 1. detects the typing language;
 * 2. English: runs [com.keylingo.app.data.translation.EnglishToSpanishTranslator]
 *    -- the sentence translation and the word hints come from the same
 *    aligned analysis, so a hint for "working" in "I am working" is
 *    "trabajando", consistent with the sentence;
 *    Spanish: glosses each word into English and restores accents;
 * 3. trims word hints to the learning mode's budget with [LearningModePolicy].
 *
 * A sentence translation is offered only when it is trustworthy: every
 * content word translated, and at least two words long (single words are
 * already covered by their word hint).
 */
@Singleton
class LearningAssistant @Inject constructor(
    private val source: BundledDataSupplier,
    private val dispatchers: AppDispatchers
) : HintProvider {

    override suspend fun assist(
        text: String, targetLanguage: String, learningMode: LearningMode, maxHints: Int?, typingLanguage: String?, checkGrammar: Boolean
    ): LearningAssistance {
        if (text.isBlank()) return LearningAssistance.EMPTY
        val data = source.get() ?: return LearningAssistance.EMPTY
        return withContext(dispatchers.compute) { analyze(data, text, learningMode, maxHints, typingLanguage, checkGrammar) }
    }

    /**
     * [text] may end with the word still being typed (no trailing space or
     * punctuation): grammar ignores that word until it is finished.
     */
    fun analyze(
        data: BundledLanguageData, text: String, learningMode: LearningMode, maxHints: Int?,
        typingLanguage: String? = null, checkGrammar: Boolean = true
    ): LearningAssistance {
        val sentence = text.trim()
        val language = typingLanguage ?: data.languageDetector.detect(sentence)
        val cap = maxHints ?: LearningModePolicy.defaultMaxHints(learningMode)
        if (language == "es") return spanishAssistance(data, sentence, learningMode, cap)
        val complete = text.isNotEmpty() && !text.last().isLetterOrDigit()
        val grammar = if (checkGrammar) data.grammarChecker.check(text.trimStart(), complete) else null
        return englishAssistance(data, sentence, learningMode, cap, grammar)
    }

    private class Candidate(val hint: HintWord, val order: Int, val posWeight: Int, val rank: Int, val cefr: CefrLevel)

    private fun trim(candidates: List<Candidate>, mode: LearningMode, cap: Int): List<HintWord> {
        val unique = candidates.distinctBy { it.hint.originalWord.lowercase() to it.hint.translatedWord }
        val survivors = if (unique.size <= cap) unique else unique
            .sortedByDescending { LearningModePolicy.keepScore(mode, it.posWeight, it.rank, it.cefr) }
            .take(cap)
            .sortedBy { it.order }
        return survivors.map { it.hint }
    }

    private fun englishAssistance(
        data: BundledLanguageData, sentence: String, mode: LearningMode, cap: Int, grammar: com.keylingo.app.domain.model.GrammarSuggestion?
    ): LearningAssistance {
        // Translate what the user meant: if the source has a grammar error, translate the corrected sentence
        // (and say so), so "Yesterday I go to the office" keeps its past tense in Spanish.
        val source = grammar?.corrected?.trim() ?: sentence
        val result = data.translator.translate(source)
        val candidates = result.segments.mapIndexedNotNull { index, seg ->
            val worthShowing = seg.isContent || (seg.formLabel == "subject" && seg.hintText.isNotEmpty())
            if (!worthShowing || seg.hintText.isBlank() || seg.sourceText.isBlank() || seg.confidence <= 0f) return@mapIndexedNotNull null
            if (seg.formLabel == "name" || seg.formLabel == "untranslated") return@mapIndexedNotNull null
            val entry = seg.entry
            val posWeight = if (seg.formLabel == "phrase") LearningModePolicy.PHRASE_CHUNK_POS_WEIGHT else LearningModePolicy.posImportance(seg.partOfSpeech)
            Candidate(
                hint = HintWord(
                    originalWord = seg.sourceText,
                    translatedWord = seg.hintText,
                    partOfSpeech = seg.partOfSpeech,
                    formLabel = seg.formLabel,
                    dictionaryEntryId = entry?.id,
                    confidence = seg.confidence
                ),
                order = index,
                posWeight = posWeight,
                rank = entry?.frequencyRank ?: 0,
                cefr = entry?.cefrLevel ?: CefrLevel.A1
            )
        }
        val words = sentence.split(Regex("\\s+")).count { it.any(Char::isLetter) }
        val translation = if (result.text.isNotEmpty() && words >= 2 && result.coverage >= 0.999f) {
            SentenceTranslation(source, result.text, "en", "es", result.coverage, fromCorrectedSource = grammar != null,
                notes = grammarNotes(result.segments.map { it.formLabel }))
        } else null
        return LearningAssistance(translation, trim(candidates, mode, cap), "en", grammar)
    }

    /** Short explanations of the Spanish grammar a translation used, most instructive first. */
    private fun grammarNotes(labels: List<String>): List<String> {
        val notes = LinkedHashSet<String>()
        for (l in labels) {
            when {
                l.startsWith("ir a") -> notes += "ir a + infinitive: near future (voy a llamar)"
                l.startsWith("estar · ") && labels.contains("gerund") -> notes += "estar + gerund: action in progress"
                l.startsWith("gustar") || l.startsWith("encantar") -> notes += "gustar: the thing liked is the subject (me gusta / me gustan)"
                l.startsWith("tener que") -> notes += "tener que + infinitive: have to"
                l.startsWith("haber · perfect") -> notes += "haber + participle: present perfect"
                l.startsWith("acabar de") -> notes += "acabar de + infinitive: to have just done"
                l.startsWith("tener ") && l.contains("=") -> notes += "Spanish uses tener for hunger, thirst, fear, age…"
                l.startsWith("weather") -> notes += "Weather uses hacer: hace frío, hace calor"
                l == "personal a" -> notes += "Personal “a” before a person who is the object"
                l.startsWith("past ·") -> notes += "Preterite: a completed past action"
                l.startsWith("imperfect") -> notes += "Imperfect: ongoing or habitual past"
                l.startsWith("future ·") -> notes += "Future: infinitive + é/ás/á/emos/án"
                l.startsWith("negative command") -> notes += "Negative commands use the subjunctive (no hables)"
                l.startsWith("ser ·") || l.startsWith("estar ·") -> notes += "ser: identity/traits · estar: states/location"
            }
        }
        return notes.take(2)
    }

    private val wordRegex = Regex("[\\p{L}']+")

    private fun spanishAssistance(data: BundledLanguageData, sentence: String, mode: LearningMode, cap: Int): LearningAssistance {
        val candidates = wordRegex.findAll(sentence).mapIndexedNotNull { index, m ->
            val gloss = data.glosser.gloss(m.value) ?: return@mapIndexedNotNull null
            if (gloss.label == "function word" && gloss.canonicalSpanish.equals(m.value, ignoreCase = true)) return@mapIndexedNotNull null
            val entry = gloss.match?.entry
            // Show the correctly accented spelling when it differs from what was typed ("tambien -> también · also").
            val original = if (gloss.canonicalSpanish.equals(m.value, ignoreCase = true)) m.value else gloss.canonicalSpanish
            Candidate(
                hint = HintWord(original, gloss.english, entry?.partOfSpeech ?: PartOfSpeech.OTHER, gloss.label, entry?.id, 0.8f),
                order = index,
                posWeight = LearningModePolicy.posImportance(entry?.partOfSpeech ?: PartOfSpeech.OTHER).coerceAtLeast(1),
                rank = entry?.frequencyRank ?: 0,
                cefr = entry?.cefrLevel ?: CefrLevel.A1
            )
        }.toList()
        return LearningAssistance(null, trim(candidates, mode, cap), "es")
    }
}
