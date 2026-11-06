# KeyLingo — Feature Implementation Status

Legend: **✅ Built + tested** (compiles, covered by passing unit tests) ·
**🟨 Built** (compiles/packaged, no automated test — mostly Compose UI) ·
**🟧 Partial** · **❌ Not implemented**. Nothing has been verified on a device yet.

## A. Keyboard and input handling

| Feature | Status | Notes / test |
|---|---|---|
| Character input, shift, caps lock (double tap) | ✅ | `KeyboardViewModelTest.doubleTapShiftLocksCaps` |
| Auto-capitalisation (sentence start, newline, `.!?¿`) that also disarms itself | ✅ | `typesAndCapitalises…`, `autoShiftTurnsOff…`, `TextContextTest.sentenceStartRules` |
| Respects field caps flags (words / all caps), none in email/URL/password | ✅ | `InputFieldPolicyTest` |
| Backspace: grapheme-safe (emoji, skin tones, ZWJ), selection, DEL key in empty fields | ✅ | `backspaceDeletesWholeEmoji…`, `backspaceInAnEmptyField…` |
| Backspace hold-to-repeat with acceleration | 🟨 | Compose gesture; device feel unverified |
| Double-space period (only after a word) | ✅ | `doubleSpaceInsertsAPeriod…` |
| Enter: field IME action (send/search/go/next/done) vs newline, icon per action | ✅ / 🟨 | logic tested; icon rendering untested |
| Cursor-relative editing (moved cursor, mid-text) | ✅ | `pickingASuggestionReplacesOnlyTheWordAtTheCursor` |
| `onUpdateSelection` refresh; external cursor moves cancel undo state | ✅ / 🟨 | ViewModel logic tested; IME callback untested |
| Symbols (2 pages), number row setting | 🟨 | pre-existing layouts kept |
| Emoji: 250+ in a scrollable grid | 🟨 | |
| Long-press accents / ñ / ¿ ¡ / punctuation (slide to select) | 🟨 | in-layout overlay; needs device check |
| Haptic feedback (setting) | 🟨 | |
| Fixed keyboard geometry (strip + keys constant height in all modes) | 🟨 | 92 dp + 260 dp; landscape not adapted |
| Number/phone fields open symbols layer | ✅ | `numberFieldsOpenTheSymbolsLayer` |
| TalkBack labels and click actions on keys/chips | 🟨 | |
| Keyboard switching (globe) | ❌ | system switcher only (`supportsSwitchingToNextInputMethod` declared, no key) |
| Swipe typing, space-bar cursor control, clipboard | ❌ | backlog |

## B. Autocomplete and dictionary

| Feature | Status | Notes |
|---|---|---|
| 61,547-word English lexicon (SCOWL + curated ranks, contractions, capitalised words) | ✅ | `LexiconTest`, `BundledDataIntegrityTest` |
| Prefix search (case/apostrophe-insensitive), frequency ranking | ✅ | |
| Case preservation (`Hel`→`Hello`, `HEL`→`HELLO`, `i`→`I`) | ✅ | |
| Keyboard-aware typo tolerance (adjacent keys, transpositions, doubled letters, typo'd prefixes) | ✅ | `autocorrectsCommonKeyboardTypos` |
| Conservative autocorrect + literal option + one-tap undo + session memory of rejections | ✅ | use-case and ViewModel tests |
| Autocorrect setting; disabled in email/URL/name/no-suggestion fields | ✅ | |
| Profanity recognised but never suggested | ✅ | flag `n` |
| Spanish suggestions with accent restoration (`tambien`→`también`, `manana`→`mañana`) | ✅ | |
| EN/ES separation (per-sentence detection, per-language personal data) | ✅ | `languagesDoNotMix`, `detectsTheTypingLanguage` |
| True corpus frequency for words outside the curated top ~1,900 | 🟧 | SCOWL tier + length heuristic (see backlog) |

## C. Translation intelligence

| Feature | Status | Notes |
|---|---|---|
| Word-level EN→ES hints, POS-aware senses (love: amar/amor) | ✅ | |
| Context-consistent hints from sentence alignment (`working`→`trabajando`) | ✅ | `segmentsAlignEnglishToSpanishForHints` |
| Sentence translation chip (rule-based, offline) | ✅ | 83 grammar cases in `EnglishToSpanishTranslatorTest` |
| Tenses: present, progressive, preterite, imperfect, future, conditional, perfect, perfect progressive, near future | ✅ | |
| Modals, negation, questions with `¿?`, imperatives (+/−), subjunctive in common triggers | ✅ | |
| ser/estar, tener idioms, weather, gustar, clitics, personal a, agreement | ✅ | |
| Incomplete sentences (translate what is typed so far) | ✅ | coverage gate hides partial output |
| Idioms (47) | ✅ | `idioms_en_es.tsv` |
| Spanish→English glosses with conjugation info and ambiguity | ✅ | `glossesSpanishConjugationsBackToEnglish` |
| Usted/ustedes register, vosotros, passive voice, relative clauses, possessive 's | ❌ | backlog |
| Speaker gender for agreement (`estoy cansada`) | ❌ | defaults to masculine — backlog P1 |

## D. Personal learning

| Feature | Status | Notes |
|---|---|---|
| Learning from naturally typed words (3 uses) and accepted suggestions (1 tap) | ✅ | Robolectric + fakes |
| Persistence across restarts (Room) | ✅ / device ❌ | DB tests use real SQLite in memory |
| Frequency + recency ranking | ✅ | |
| Duplicate prevention + concurrency safety (atomic upserts) | ✅ | 60-way concurrent test |
| Validation (no numbers/punctuation/mashing/very long) | ✅ | `invalidInputIsNeverStored` |
| Never learns in password/incognito fields | ✅ | ViewModel + policy tests |
| Undoing an autocorrection teaches the word | ✅ | |
| Delete all learned data (Settings) | ✅ / 🟨 | provider tested; UI untested |

## E. Next-word prediction

| Feature | Status | Notes |
|---|---|---|
| Personal bigrams | ✅ | |
| Personal trigrams (Context DB v2, non-destructive migration) | ✅ | migration test validates schema |
| Cold-start model (EN 76 heads, ES 21 heads, sentence start) | ✅ | `predictsNextWordWithNoHistory` |
| Context only re-ranks prefix matches | ✅ | `contextReranksButNeverIntroduces…` |
| Capitalised predictions at sentence start | ✅ / 🟨 | |

## F. Platform, privacy, performance

| Item | Status | Notes |
|---|---|---|
| Offline only, no INTERNET permission | ✅ | manifest |
| No typed text in logs | ✅ | only counts/timings/exception types are logged |
| Background coroutine failures cannot crash the IME | 🟨 | `CoroutineExceptionHandler` in service scope |
| `directBootAware` removed (CE storage unavailable before unlock) | 🟨 | |
| Heavy work off the main thread; bounded context reads | ✅ / 🟨 | |
| Bundled data load ≈150–270 ms on desktop JVM (background) | ✅ measured on JVM | phone timing unverified |
| Release shrinking (R8) | 🟨 | builds (1.95 MB); never run |
