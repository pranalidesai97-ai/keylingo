# Changelog

## 2026-11-07 — Language help in browser and search fields; slimmer suggestion strip

**Device report (vivo V2515, build a2e98e6).** WhatsApp showed "Estoy bebiendo agua" for "I am drinking water". Chrome's search field and YouTube's search field showed only English word suggestions, with no translation.

**Root cause.** `InputFieldPolicy.allowLearningHints`, the single gate for translation, word hints and grammar chips, required a "prose" field. Prose excluded the `TYPE_TEXT_VARIATION_URI` variation (used by browser address/search bars), `TYPE_TEXT_VARIATION_FILTER` (common for search boxes) and any field without the text class (`TYPE_NULL`). Word suggestions use a separate, looser gate (not password, not numeric), so those fields got words but no Spanish. No app-specific code was involved.

**Fix.**
- Read-only language help now has its own rule: every text field (and `TYPE_NULL`) except passwords and e-mail addresses.
- Autocorrect, auto-capitals and learning keep their previous rules: still off in address/search bars and incognito.
- Text that is a URL or e-mail (`www.…`, `https://…`, `name@host.tld`, `host.com`) gets no translation or grammar chips.
- Enter still performs the field's action (GO/SEARCH/SEND). Nothing is applied without a tap.

**Layout.**
- The strip was always 92 dp: two 46 dp rows, even where the second row could never have content.
- Now the word row is 40 dp, with an explicit line height so Large text is not clipped. The 46 dp learning row is reserved only when the field allows language help and the learning row or grammar is on.
- The height is decided per field and settings, so keys never move while typing.
- Assisted fields: 86 dp (was 92). Others: 40 dp. Keys, themes and colours are unchanged.

**Tests.** +15:
- `InputFieldPolicyTest`: 21 named field configurations (chat, browser address bar normal and incognito, web search input, spellcheck-off input, SearchView, filter box, TYPE_NULL, passwords, e-mail, numbers…), plus actions and learning.
- `KeyboardViewModelTest`: typing through the keyboard in each field type, Enter action, URL/e-mail skipping, restricted fields, nothing learned in search fields.
- `StripLayoutTest`: measured heights per field and setting, constant while typing, renders.

The ICU regex check also covers the new address pattern.

## 2026-11-07 — Fix: keyboard service crashed at startup on Android

**Device report (vivo phone, build 493dd6e).** `Unable to create service KeyLingoInputMethodService`, caused by `PatternSyntaxException: Syntax error in regexp pattern near index 3` at `LearningAssistant.<init>`.

**Root cause.**
- The Spanish grammar-note patterns started with the inline flag `(?U)` (UNICODE_CHARACTER_CLASS).
- Android's `java.util.regex` is ICU4C, which rejects that flag. Reproduced with ICU 74.2: `U_REGEX_RULE_SYNTAX` at offset 3.
- The patterns were compiled in `LearningAssistant`'s constructor, which Hilt runs while creating the keyboard service, so the service never started.
- JVM unit tests could not catch it because desktop Java accepts `(?U)`.

**Fix (no linguistic change).**
- The ten rules moved to `SpanishGrammarNotes`. `(?U)` is removed.
- `\b` is replaced by explicit look-arounds on Unicode letters, marks and digits. Desktop Java and ICU disagree on `\b` next to accented letters; the look-arounds behave identically in both.
- Input is NFC-normalised, so decomposed accents still match.
- Patterns compile lazily on first use (background dispatcher), never during service creation. A pattern the platform rejects is skipped instead of crashing.
- Spanish progressive detection (estoy trabajando, estás comiendo, estamos viviendo, estaba estudiando…) and the other nine notes are unchanged.

**New safeguards.**
- `SpanishGrammarNotesTest`: production patterns on progressive examples, accents, capitals, NFD, punctuation, English, empty input, word boundaries, and the real `LearningAssistant` path.
- `AndroidRegexCompatibilityTest`: statically rejects syntax ICU does not support (inline flags other than i/m/s/x/w, Java-only properties, unbounded look-behind) in all 29 production patterns. It also writes `build/android-regex-cases.tsv`.
- `tools/android-regex-check/run.sh`: replays that file in ICU4C (Android's engine). Result: 29 patterns compile, and all 283 match checks agree with the JVM. Both checks fail on the old `(?U)` pattern.

## 2026-11-06 — Audit and improvement phase (Phases 1–9)

Commits on `claude/eager-dijkstra-jsufzj`, in order:
- d2ef600 Phase 1, key feedback.
- 8312171 Phase 6, themes. Done second, together with the feedback work, as agreed.
- 87662db Phase 2, vocabulary.
- 5a70f01 Phase 3, grammar.
- 503009a Phase 4, translation.
- 20dadd2 Phases 5 and 7, learning data and settings.
- The Phase 8/9 commit: privacy, latency test, docs.

Verified by the build/test results in PROJECT_STATE.md; nothing was device-tested.

### Phase 1 — Key feedback
- `KeyFeedback` is the single place that vibrates or plays a key sound. Vibration and sound have separate settings, both **off by default**. `VIBRATE` permission removed (`KEYBOARD_TAP` needs none).
- Pressed state on every key: face tint and 0.95 scale.
- Backspace repeat rewritten as one gesture loop: 1 delete on press, repeat after 400 ms at 55 ms, then 35 ms. Stops on release or cancel (Compose gesture test).

### Phase 2 — Vocabulary and autocomplete
- English lexicon: 61,547 → **111,795** words.
  - SCOWL levels up to 60 are suggestable; level 70 is recognised-only.
  - Curated proper nouns added; `cant`/`wont` demoted.
- 108 curated misspellings, preferred as the autocorrection.
- Distant typos are no longer shown next to real completions, and the best completion always has a slot.
- Typo search re-indexed (length buckets, banded DP) so the larger lexicon stays within the 15 ms budget.
- Typing-language setting: Auto / English / Spanish.

### Phase 3 — Grammar suggestions
- New `EnglishGrammarChecker`: repeated words, agreement, past tense with time markers, a/an, missing articles, confusables, capitalisation, spacing.
- Shown as a chip; applied only on tap, and only if the sentence is unchanged.
- Never checked on a half-typed word.
- The sentence translation uses the corrected text and says so.

### Phase 4 — Translation
- New `SpanishToEnglishTranslator` and sentence chip for Spanish typing, with grammar notes. 112 regression sentences, including "¿Cómo estás?", "También.", "Mañana.", "Estoy aprendiendo español.", "Ayer fui al mercado." and "Quiero aprender inglés.".
- Reverse dictionary keeps every tense × person reading. `es_en_extra.tsv` adds 27 reverse-only Spanish words.
- EN→ES: "Where are you going?" → "¿Adónde vas?"; the 9 requested English examples are now regression tests.

### Phase 5 — Personal learning
- Retention: unlearned words expire after 30 days and single-use sequences after 60 days. Sequence tables are capped at 20k/30k.
- Forgetting a word removes its sequences too. No schema change.

### Phase 6 — Themes
- Sunlight Yellow (default), Lavender Purple, Dark Violet, Mint Green, Ocean Blue, Blush Pink, and a custom colour (14 swatches plus a hue slider).
- Separate key, special-key, strip and translation-chip colours; pressed and highlighted states; light/dark.
- WCAG-checked contrast; persisted; key geometry unchanged.

### Phase 7 — Settings
- Grouped sections. New controls: typing language, sentence translation, grammar suggestions.
- New learned-words screen: filter, delete one, delete all.
- Whole-row touch targets for switches.

### Phase 8 — Offline, privacy, performance
- Merged manifest checked: no INTERNET permission.
- Cloud backup and device transfer disabled for app data.
- `AssistanceLatencyTest`: all per-sentence work under 1 ms on the build JVM.

### Phase 9 — Tests
98 → **156** tests in 21 suites (see TESTING.md). New suites:
- `KeyFeedbackTest`, `BackspaceGestureTest`, `KeyboardScreenshotTest`;
- `SettingsPersistenceTest`, `KeyboardThemesTest`, `VocabularyMetricsTest`;
- `EnglishGrammarCheckerTest`, `SpanishToEnglishTranslatorTest`;
- `SettingsScreensTest`, `AssistanceLatencyTest`.

---

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
