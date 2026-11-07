# KeyLingo — Feature Implementation Status

Legend: **✅ Built + tested** (compiles, covered by passing unit tests) ·
**🟨 Built** (compiles/packaged, no automated test) · **🖼 Rendered** (Robolectric PNG reviewed, not a device) ·
**🟧 Partial** · **❌ Not implemented**. Nothing has been verified on a device yet.

## A. Keyboard and input handling

| Feature | Status | Notes / test |
|---|---|---|
| Character input, shift, caps lock (double tap) | ✅ | `KeyboardViewModelTest.doubleTapShiftLocksCaps` |
| Auto-capitalisation (sentence start, newline, `.!?¿`) that also disarms itself | ✅ | `typesAndCapitalises…`, `autoShiftTurnsOff…`, `TextContextTest.sentenceStartRules` |
| Respects field caps flags (words / all caps), none in email/URL/password | ✅ | `InputFieldPolicyTest` |
| Backspace: grapheme-safe (emoji, skin tones, ZWJ), selection, DEL key in empty fields | ✅ | `backspaceDeletesWholeEmoji…`, `backspaceInAnEmptyField…` |
| Backspace hold-to-repeat with acceleration, stops immediately on release | ✅ | `BackspaceGestureTest` (Compose gesture on Robolectric); device feel unverified |
| Double-space period (only after a word) | ✅ | `doubleSpaceInsertsAPeriod…` |
| Enter: field IME action (send/search/go/next/done) vs newline, icon per action | ✅ / 🟨 | logic tested; icon rendering untested |
| Cursor-relative editing (moved cursor, mid-text) | ✅ | `pickingASuggestionReplacesOnlyTheWordAtTheCursor` |
| `onUpdateSelection` refresh; external cursor moves cancel undo state | ✅ / 🟨 | ViewModel logic tested; IME callback untested |
| Symbols (2 pages), number row setting | 🟨 | pre-existing layouts kept |
| Emoji: 250+ in a scrollable grid | 🟨 | |
| Long-press accents / ñ / ¿ ¡ / punctuation (slide to select) | 🟨 | in-layout overlay; needs device check |
| Vibration on key press: **off by default**, setting persists | ✅ | `KeyFeedbackTest`, `SettingsPersistenceTest`; `KeyFeedback` is the only haptic call site; no `VIBRATE` permission |
| Key sound: separate setting, off by default, system volume | ✅ | `KeyFeedbackTest` (Delete/Space/Return/standard effects) |
| Visual pressed state (face tint + 0.95 scale) on every key | 🖼 | `KeyboardScreenshotTest` |
| Keyboard geometry: keys 260 dp in all modes and themes; strip 40 dp word row + 46 dp learning row, or the word row alone in fields without language help / with learning and grammar off; constant while typing | ✅ / 🖼 | `StripLayoutTest` (measured heights, renders); landscape not adapted |
| Translation, word hints and grammar chips in browser address bars, search boxes (`textUri`, `textFilter`, web inputs, SearchView) and untyped fields; never in passwords or e-mail fields; URLs/e-mails typed in them are skipped | ✅ | `InputFieldPolicyTest` (21 field configurations), `KeyboardViewModelTest` cross-app tests; **device-confirmed only for WhatsApp so far** |
| Themes: Sunlight Yellow (default), Lavender Purple, Dark Violet, Mint Green, Ocean Blue, Blush Pink + custom colour; light/dark; persisted | ✅ / 🖼 | `KeyboardThemesTest` (WCAG contrast for every preset and 14 swatches × light/dark), `SettingsPersistenceTest`, rendered previews |
| Number/phone fields open symbols layer | ✅ | `numberFieldsOpenTheSymbolsLayer` |
| TalkBack labels and click actions on keys/chips | 🟨 | |
| Keyboard switching (globe) | ❌ | system switcher only (`supportsSwitchingToNextInputMethod` declared, no key) |
| Swipe typing, space-bar cursor control, clipboard | ❌ | backlog |

## B. Autocomplete and dictionary

| Feature | Status | Notes |
|---|---|---|
| 111,795-word English lexicon (77,137 suggestable + 34,658 recognised-only; 48,652 inflected forms; proper nouns) | ✅ | `LexiconTest`, `VocabularyMetricsTest`, `BundledDataIntegrityTest` |
| Curated misspellings (108: `becuase`, `definately`, `tomorow`, `recieve`, `teh`, `alot`…) preferred as the autocorrection | ✅ | `GenerateAutocompleteSuggestionsUseCaseTest` |
| No unrelated typo suggestions next to good completions; best completion always visible | ✅ | same |
| Prefix search (case/apostrophe-insensitive), frequency ranking | ✅ | |
| Case preservation (`Hel`→`Hello`, `HEL`→`HELLO`, `i`→`I`) | ✅ | |
| Keyboard-aware typo tolerance (adjacent keys, transpositions, doubled letters, typo'd prefixes) | ✅ | `autocorrectsCommonKeyboardTypos` |
| Conservative autocorrect + literal option + one-tap undo + session memory of rejections | ✅ | use-case and ViewModel tests |
| Autocorrect setting; disabled in email/URL/name/no-suggestion fields | ✅ | |
| Profanity recognised but never suggested | ✅ | flag `n` |
| Spanish suggestions with accent restoration (`tambien`→`también`, `manana`→`mañana`) | ✅ | |
| EN/ES separation (per-sentence detection, per-language personal data) | ✅ | `languagesDoNotMix`, `detectsTheTypingLanguage` |
| Typing language setting (Auto / English / Spanish); forced Spanish gives no English suggestions | ✅ | `KeyboardViewModelTest`, `SettingsScreensTest` |
| True corpus frequency for words outside the curated top ~1,900 | 🟧 | SCOWL tier + length heuristic (see backlog) |

## C. Translation intelligence

| Feature | Status | Notes |
|---|---|---|
| Word-level EN→ES hints, POS-aware senses (love: amar/amor) | ✅ | |
| Context-consistent hints from sentence alignment (`working`→`trabajando`) | ✅ | `segmentsAlignEnglishToSpanishForHints` |
| English→Spanish sentence translation chip (rule-based, offline) | ✅ | 96 sentences in `EnglishToSpanishTranslatorTest`, incl. the 9 requested examples |
| Tenses: present, progressive, preterite, imperfect, future, conditional, perfect, perfect progressive, near future | ✅ | |
| Modals, negation, questions with `¿?`, imperatives (+/−), subjunctive in common triggers | ✅ | |
| ser/estar, tener idioms, weather, gustar, clitics, personal a, agreement | ✅ | |
| Incomplete sentences (translate what is typed so far) | ✅ | coverage gate hides partial output |
| Idioms (47) | ✅ | `idioms_en_es.tsv` |
| Spanish→English glosses with conjugation info and ambiguity | ✅ | `glossesSpanishConjugationsBackToEnglish` |
| **Spanish→English sentence translation** (dropped subjects, do-support, questions with postverbal subjects, gustar/doler, tener/hacer/haber idioms, ages, verb chains, enclitics, set phrases, unaccented questions) | ✅ | `SpanishToEnglishTranslatorTest` — 112 sentences incl. the 6 requested examples; chip shown only at 100% coverage |
| Grammar notes on the sentence chip (estar + gerund, ir a, gustar, tener que, preterite…) | ✅ | `LanguageAndGlossTest.spanishSentenceChip…` |
| Translation of the grammar-corrected source, labelled “from corrected sentence” | ✅ | `KeyboardViewModelTest` |
| `where` + motion → `adónde` | ✅ | `EnglishToSpanishTranslatorTest.whereWithMotionTakesAdonde` |
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
| Delete all learned data (Settings) | ✅ | provider + Compose confirmation-dialog test |
| Learned-words screen: list, filter, delete single words | ✅ / 🖼 | `SettingsScreensTest`; forgetting also removes the word’s sequences (`UserDatabasesTest`) |
| Retention: incidental words forgotten after 30 days, single-use sequences after 60 days, sequence caps 20k/30k | ✅ | `UserDatabasesTest` (runs once per keyboard start) |

## C2. Grammar suggestions (never automatic)

| Feature | Status | Notes |
|---|---|---|
| Repeated words (`the the`), allowed repeats (`had had`, `that that`) | ✅ | `EnglishGrammarCheckerTest` |
| Subject–verb agreement (`she go`, `they is`, `my friends was`) with compound-noun, frequency-adverb and subjunctive guards | ✅ | |
| Past tense with past time markers (`Yesterday I go` → `went`), blocked by future markers | ✅ | |
| a/an (incl. `an hour`, `a university`), missing articles (`go to market`, `I am teacher`) | ✅ | |
| Confusables: `could of`, `their is`, `your welcome`, `its a`, comparative `then`→`than` | ✅ | |
| Capitalisation of `i` and sentence start (complete sentences only), spacing before punctuation | ✅ | |
| Informal English and incomplete sentences not flagged; checked only after a finished word | ✅ | |
| Shown as a tappable chip; applying replaces the sentence only if unchanged | ✅ / 🖼 | `KeyboardViewModelTest`, rendered row |
| Grammar setting | ✅ | `SettingsScreensTest` |
| Deeper grammar (sentence structure, prepositions in general, tense sequences) | ❌ | rule-based scope; backlog |

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
| Offline only, no INTERNET permission | ✅ | checked in the **merged** release manifest (no library adds it) |
| Personal data excluded from cloud backup and device transfer | 🟨 | `allowBackup=false` + `data_extraction_rules.xml` |
| No typed text in logs | ✅ | only counts/timings/exception types are logged |
| Background coroutine failures cannot crash the IME | 🟨 | `CoroutineExceptionHandler` in service scope |
| `directBootAware` removed (CE storage unavailable before unlock) | 🟨 | |
| Heavy work off the main thread; bounded context reads | ✅ / 🟨 | |
| Bundled data load ≈0.4–0.8 s on the build JVM (background; larger lexicon) | ✅ measured on JVM | phone timing unverified |
| Per-sentence work (grammar, EN→ES, ES→EN, hint analysis) < 1 ms each on the build JVM | ✅ | `AssistanceLatencyTest` (budgets 5–25 ms) |
| Typo lookup ≈6–10 ms per call on the build JVM | ✅ | `LexiconTest` budget 15 ms; slowest per-keystroke step |
| Release shrinking (R8) | 🟨 | builds (2.5 MB); never run |

## G. Settings

| Item | Status | Notes |
|---|---|---|
| Sections: Typing · Appearance · Learning & translation · Personal dictionary · About | ✅ / 🖼 | `SettingsScreensTest` |
| Typing: autocorrect, number row, vibration (off), sound (off), typing language | ✅ | whole row is the touch target |
| Appearance: theme presets + custom swatches/hue, dark mode, hint size | ✅ / 🖼 | |
| Learning: learning row, sentence translation, grammar suggestions, learning mode, learning language | ✅ | |
| Personal dictionary: learned-words manager, delete all | ✅ / 🖼 | |
