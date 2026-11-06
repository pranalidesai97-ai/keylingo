package com.keylingo.app.domain.model

/**
 * A single dictionary entry, independent of how it is stored (Room today;
 * could be a remote source or an on-device model later).
 *
 * Verbs carry up to three surface translations -- [baseTranslation] (the
 * present-tense "yo" form, e.g. "trabajo"), [infinitiveTranslation] (e.g.
 * "trabajar"), and [gerundTranslation] (e.g. "trabajando") -- because the
 * *same* English lemma legitimately needs different Spanish surface forms
 * depending on the grammar pattern it appears in. Non-verbs only ever
 * populate [baseTranslation]; [translationFor] transparently falls back to
 * it if a form-specific translation isn't available.
 *
 * [id] is the stable row identifier from storage. It isn't used by
 * translation logic itself, but future features (tap-to-expand, favorites,
 * spaced repetition, "mark as learned") all need something durable to key
 * off of -- carrying it here now avoids reshaping this model again later.
 *
 * [cefrLevel] and [frequencyRank] both describe "how advanced/common is
 * this word" but answer different questions: CEFR is a standard difficulty
 * band (useful for learning-mode gating later), frequencyRank is a raw
 * ordering used today for hint-trimming when a sentence has more
 * candidates than fit in the hint bar.
 */
data class DictionaryEntry(
    val id: Long,
    val sourceWord: String,
    val baseTranslation: String,
    val infinitiveTranslation: String?,
    val gerundTranslation: String?,
    val sourceLanguage: String,
    val targetLanguage: String,
    val partOfSpeech: PartOfSpeech,
    val frequencyRank: Int,
    val cefrLevel: CefrLevel
) {
    /** Resolves the correct surface translation for the requested [form], falling back to the base form. */
    fun translationFor(form: GrammaticalForm): String = when (form) {
        GrammaticalForm.BASE -> baseTranslation
        GrammaticalForm.INFINITIVE -> infinitiveTranslation ?: baseTranslation
        GrammaticalForm.GERUND -> gerundTranslation ?: baseTranslation
    }
}
