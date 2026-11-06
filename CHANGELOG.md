# Changelog

## 2026-11-06 — Engineering pass: first build, data repair, engine, keyboard integration

Starting point: `FINAL_SOURCE_2.zip`, imported unchanged as commit `df4f327`.
The baseline had never been compiled. All items below are in the code and
covered by the build/test results in PROJECT_STATE.md unless marked
"device-unverified".

### Build
- Added the missing `gradle/wrapper/gradle-wrapper.jar`; replaced the broken
  hand-written `gradlew`/`gradlew.bat` (failed with `Could not find or load main class "-Xmx64m"`).
- Added test source set, Robolectric/coroutines-test/room-testing test deps,
  Room schema export (`app/schemas/`).
- ProGuard: removed `-keep class androidx.compose.**` / Hilt keep-alls that
  disabled shrinking (release APK 7.0 MB → 1.95 MB) and a stale rule.

### Data (bundled assets)
- **Restored Spanish accents and ñ** that had been stripped from all 905
  legacy entries (several became different or offensive words: `año`→`ano`,
  `bebé`→`bebe` "drinks", `trabajó`→`trabajo` "I work").
- Dictionary migrated to lemma-based `en_es.tsv`: inflected English rows
  with hard-coded 3rd-person translations removed (conjugation is generated),
  noun gender tagged, POS-aware senses (fixes the unique-index collision that
  dropped one sense of `love`, `plan`, `open`...), wrong translations fixed
  (`you`→`tú`, `what`→`qué`, `agree`→`estar de acuerdo`...), 420+ entries
  added (1,325 total).
- New English lexicon: 61,547 words from SCOWL + curated ranks (was 1,798
  with unreliable ranks, e.g. `please` 1686). Reproducible build script in `tools/`.
- Idioms (47) replace the 35 "phrase chunks"; grammar moved into rules.
- Cold-start next-word tables for English and Spanish.

### Engine (new, pure Kotlin)
- `SpanishConjugator`, `SpanishMorphology`, `SpanishText` (accent folding, clitic stress).
- `EnglishMorphology` (irregular verbs, inflection analysis).
- `Lexicon` + `TypoDistance` (keyboard-aware weighted Damerau–Levenshtein, single-pass prefix/whole scan).
- `EnglishToSpanishTranslator` (rule-based sentence translation with alignments).
- `SpanishToEnglishGlosser`, `TypingLanguageDetector`.

### Keyboard
- Context read relative to the cursor (240 chars) instead of the whole field
  with an end-of-field assumption (suggestion taps deleted the wrong text after cursor moves).
- Grapheme-safe Backspace; DEL key event in empty fields.
- Autocorrect with highlighted candidate, literal option, one-tap undo,
  per-session rejections; autocorrect setting.
- Picked suggestions keep typed case; the inserted space moves behind punctuation.
- Auto-shift disarms when no longer at a sentence start; respects field caps flags.
- Double-space period no longer fires after punctuation (`!!` / `..` bug).
- Enter follows the field action with matching icon.
- Next-word prediction (personal trigrams > bigrams > cold start).
- Learning row: sentence translation + context-consistent word hints with
  grammar labels; English glosses when typing Spanish; Spanish suggestions.
- Long-press accents/ñ/¿¡/punctuation, haptics, emoji grid, space-bar
  language label, TalkBack semantics (device-unverified).
- Hint/suggestion work runs on background dispatchers; debounced 20/220 ms.

### Reliability and privacy
- **Crash fix:** personal-data writes used find-then-insert on a unique
  index; concurrent boundary events threw `SQLiteConstraintException` inside
  fire-and-forget coroutines → atomic upserts in transactions, plus a
  `CoroutineExceptionHandler` on the service scope.
- **Privacy fix:** `InputFieldPolicy` — no analysis/learning in password
  fields, no learning in incognito fields, no autocorrect/caps in
  email/URL/name fields, symbols layer for numeric fields.
- Removed `directBootAware` (credential-encrypted DBs unavailable before first unlock).
- Settings: "Delete learned words" clears both personal databases.
- Context DB v2 (trigrams) with a validated non-destructive migration.
- Legacy bundled-data cache DBs deleted on startup; user DBs untouched.

### Removed (superseded, no behaviour lost)
Room cache layer for bundled data (`DictionaryDatabase`, `AutocompleteDatabase`,
seeders, `RoomDictionaryProvider`, `RoomAutocompleteProvider`,
`RoomPhraseChunkProvider`), `SentenceAnalyzer`/`PhraseMatcher`/`DefaultHintProvider`
(replaced by `LearningAssistant` + translator), unused `DictionaryProvider`,
`TranslationProvider`, `SourceLanguageRules`, `PhraseChunk(Provider)`, `Bigram`,
the `KLDebug`-era `TODO.md`/`DEBUG_STATE.md` (superseded by KNOWN_ISSUES_AND_BACKLOG.md).

### Tests
98 unit tests (previously none). See TESTING.md.

---

## Earlier sessions (summarised from the previous CHANGELOG/PROJECT_STATE)
Backspace hold-to-repeat, auto-capitalisation, double-space period,
configurable number row, fixed-height suggestion strip, personal
vocabulary and bigram tables (v1), learning modes, dual-row hint bar. None of
these were ever compiled or run at the time.
