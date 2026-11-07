# KeyLingo — Testing

## 1. Running the tests

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:lintDebug :app:assembleDebug :app:assembleRelease
```

Unit tests run on the JVM. They load the **real** bundled assets from
`app/src/main/assets` (via `testing/TestData.kt`), so they test the shipped
data, not fixtures. Database tests use Robolectric (`@Config(sdk = [34])`);
the first run downloads Robolectric's Android runtime jar (~150 MB) from
Maven Central. Reports: `app/build/reports/tests/testDebugUnitTest/index.html`.

## 2. Current results (2026-11-06)

**182 tests, 0 failures, 24 suites** (2026-11-07, after the cross-app fix).

| Suite | Tests | What it covers |
|---|---|---|
| `KeyboardViewModelTest` | 30 | Real typing sequences into a simulated text field. Covers auto-caps, grapheme backspace, double-space and autocorrect with undo. Also picks, prediction, learning, password/incognito/email fields, Enter actions, translation and hints. New: forced Spanish mode, grammar suggested but not applied, translation of the corrected source, grammar waits for a finished word, grammar off. |
| `GenerateAutocompleteSuggestionsUseCaseTest` | 21 | Ranking, case, typos, curated misspellings (`becuase`, `definately`, `tomorow`, `recieve`, `teh`). No junk beside good completions; proper nouns in the top 3; rare real words never corrected; Spanish misspellings; learned words; context re-ranking. |
| `EnglishToSpanishTranslatorTest` | 14 | 96 sentences: the 9 requested examples, `adónde`, tenses, modals, clitics, gustar, ser/estar/tener, questions, subjunctive, agreement, idioms, alignments. |
| `SpanishToEnglishTranslatorTest` | 13 | 112 sentences: the 6 requested examples, dropped subjects, negation, questions, ages, unaccented questions, verb chains, gustar/doler, idioms, ser/estar, prepositions, set phrases. Unknown words must lower coverage. |
| `UserDatabasesTest` (Robolectric SQLite) | 11 | Concurrency, threshold, validation, language separation, n-grams, clear-all, v1→v2 migration. New: retention, sequence caps, listing, forgetting a word everywhere. |
| `SpanishConjugatorTest` | 10 | Full paradigms. |
| `EnglishGrammarCheckerTest` | 8 | Repeated words, agreement, past tense, articles, confusables, capitalisation. Also: no flags on informal or incomplete sentences or compound nouns, and edits are spliced correctly. |
| `BundledDataIntegrityTest` | 7 | Asset rows, senses, conjugation, gender, accents, lexicon, seeds. |
| `InputFieldPolicyTest` | 11 | Field-type rules, including 21 cross-app field configurations (chat, browser address bar, web/app search fields, TYPE_NULL, passwords, e-mail, numbers). |
| `LanguageAndGlossTest` | 5 | Detection, glosses, assistant consistency. New: the Spanish sentence chip with notes, hidden for partial or single-word input. |
| `SpanishMorphologyTest` | 5 | Plurals, agreement, articles, stress. |
| `LexiconTest` | 5 | Size, prefix keys, distances, speed (< 15 ms per typo lookup; measured 6–10 ms). |
| `TextContextTest` | 4 | Word/sentence extraction. |
| `KeyboardThemesTest` | 4 | Every preset plus 14 custom swatches, light and dark, reach WCAG contrast. Sunlight Yellow is the default; dark-mode rules. |
| `KeyboardScreenshotTest` (Robolectric Compose) | 4 | Renders every theme, the grammar row, the translation row and the theme picker to PNG. |
| `SettingsScreensTest` (Robolectric Compose) | 4 | Learned-words filter, single delete, delete-all confirmation, empty state. Settings sections and persisted changes. Renders PNGs. |
| `KeyFeedbackTest` | 3 | Vibration and sound are independent, off by default, with the right sound per key. |
| `BackspaceGestureTest` (Robolectric Compose) | 2 | Hold repeats; release stops immediately. |
| `SettingsPersistenceTest` | 2 | Vibration/sound and theme survive a new DataStore instance. |
| `AssistanceLatencyTest` | 1 | Per-sentence grammar / EN→ES / ES→EN / full analysis under budget (measured < 1 ms each). |
| `VocabularyMetricsTest` | 1 | Prints vocabulary counts (`VOCAB …` lines). |
| `StripLayoutTest` (Robolectric Compose) | 6 | Strip height per field and setting (86 dp assisted, 40 dp otherwise); unchanged while typing; Large hint size; renders `strip-*.png`. |
| `SpanishGrammarNotesTest` | 8 | Production Spanish note patterns: progressive constructions, accents, capitals, NFD, punctuation, word boundaries, English, empty input, all ten rules, and the real `LearningAssistant` path. |
| `AndroidRegexCompatibilityTest` | 3 | No Android-incompatible regex syntax in any production pattern. A checker self-test uses the exact pattern that crashed. Writes the cases for the ICU check. |

PNG renders are written to `app/build/keyboard-screenshots/`. They are
Robolectric JVM renders used for visual review, **not device screenshots**.

Design notes:
- The ViewModel runs on a `StandardTestDispatcher` that shares the test scheduler; work in `backgroundScope` would not run under `advanceUntilIdle`.
- Bundled providers take `AppDispatchers`, so all timing is virtual and deterministic.
- Compose screenshots draw the root view into a Bitmap, because `captureToImage` times out under Robolectric.

### Android regex check (ICU4C)
Unit tests run on a desktop JVM, whose regex engine accepts syntax Android's (ICU4C) rejects. That gap let a `(?U)` pattern crash the keyboard service on a phone. After the unit tests, run:

```bash
tools/android-regex-check/run.sh
```

It needs `g++` and ICU headers (`apt-get install libicu-dev`). It compiles every production pattern with ICU and replays the match checks; it must report 0 failures.

## 3. What is NOT automatically tested
- The `InputMethodService` itself, and the InputConnection behaviour of real apps.
- Compose UI on a real GPU/IME window. The Robolectric tests cover gestures, settings and rendering on the JVM only.
- The release (R8) build at runtime.
- Performance and memory on real hardware. The latency tests are desktop-JVM measurements.

## 4. Device verification checklist (outstanding — must be done on a phone)

1. Install debug APK; enable KeyLingo in system settings via the onboarding screen; switch to it.
2. Type in WhatsApp/Messages, Gmail (subject + body), Chrome address bar, a search box, a password field, a PIN field.
3. Check: first-keystroke suggestions after cold start (data load time), key latency, no dropped fast taps.
4. Autocorrect: `teh ` → `the `, Backspace restores `teh`; `dont ` → `don't `; `tambien ` while typing Spanish → `también `.
5. Long-press `e`, `n`, `?` → popup above the key, slide selects, release types; top row popup not clipped.
6. Backspace hold repeat/acceleration stops the instant you lift; emoji deletion; selection deletion; DEL in empty field removes chips.
7. Enter shows Send/Search/Next icons and performs the action; newline in multi-line fields.
8. Learning row: sentence translation appears for "I am going to call you tomorrow"; word hints readable at all hint sizes; dark mode.
9. Password field: no suggestions; afterwards no password words suggested anywhere.
10. Settings toggles apply live (number row, autocorrect, vibration, sound, typing language, learning row, sentence translation, grammar, dark mode); "Delete learned words" and single-word delete work.
11. Rotate to landscape; check keyboard height is acceptable.
12. Upgrade test: install the previous build, type, install this build → learned words kept, context DB migrated.
13. Release build (signed in Android Studio) behaves like debug.
14. TalkBack: keys announce and type on double-tap.
15. Vibration is off on a fresh install; turning it on vibrates once per key (respects the system touch-vibration switch); sound plays at system volume only when enabled.
16. Each theme (and a custom colour) looks right in light and dark on a real screen; key geometry identical across themes; pressed state visible.
17. Grammar chip: type "Yesterday I go to the market. " → chip offers "Yesterday I went to the market."; text changes only after tapping it.
18. Spanish: type "Estoy aprendiendo español." → chip "I am learning Spanish." with the estar + gerund note.
