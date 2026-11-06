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

**98 tests, 0 failures.**

| Suite | Tests | What it covers |
|---|---|---|
| `KeyboardViewModelTest` | 21 | Real typing sequences into a simulated text field: auto-caps on/off, grapheme backspace, DEL key, double-space, autocorrect on space/punctuation + undo + no re-correction, setting off, case-preserving pick + space/punctuation swap, mid-text pick, prediction, learning, password/incognito/email fields, Enter actions, number fields, caps lock, sentence translation + hints, Spanish typing, learning row off |
| `GenerateAutocompleteSuggestionsUseCaseTest` | 16 | Completion ranking, case, 8 common typos, apostrophe/capital fixes, real words never corrected, digits/mixed case, rejected corrections, literal option, learned words, threshold, Spanish accent restoration, language separation, cold-start, personal > seed, trigrams, context only re-ranks |
| `EnglishToSpanishTranslatorTest` | 12 | 83 sentences across tenses, modals, clitics, gustar, ser/estar/tener, questions, subjunctive, agreement, idioms, unknown words, alignments |
| `SpanishConjugatorTest` | 10 | Full paradigms: regular, stem-changing, orthographic, irregular, non-finite, reflexive, imperative |
| `UserDatabasesTest` (Robolectric) | 7 | 60-way concurrent writes, threshold, validation, language separation, n-gram atomicity, clear-all, v1→v2 migration |
| `BundledDataIntegrityTest` | 7 | Rows parse, unique senses, verbs conjugate, gender, no accent-stripped words, lexicon clean, seeds known |
| `InputFieldPolicyTest` | 7 | Password/incognito/email/URL/name/number/multi-line rules |
| `SpanishMorphologyTest` | 5 | Plurals with accent shifts, agreement, articles, clitic stress, folding |
| `LexiconTest` | 5 | Size/coverage, prefix keys, Spanish forms, key distances, speed (< 15 ms per typo lookup; measured ~6 ms) |
| `LanguageAndGlossTest` | 4 | Detection, ES→EN glosses, assistant consistency, learning-mode budget |
| `TextContextTest` | 4 | Word/sentence extraction, sentence start, grapheme lengths |

Design notes: the ViewModel runs on a `StandardTestDispatcher` sharing the
test scheduler (work in `backgroundScope` would not run under
`advanceUntilIdle`), and bundled providers take `AppDispatchers`, so all
timing is virtual and deterministic.

## 3. What is NOT automatically tested
- Compose UI (keys, popups, strip rendering, settings screen) — no instrumented/UI tests yet.
- The `InputMethodService` itself (InputConnection behaviour of real apps).
- Release (R8) build at runtime.
- Performance and memory on real hardware.

## 4. Device verification checklist (outstanding — must be done on a phone)

1. Install debug APK; enable KeyLingo in system settings via the onboarding screen; switch to it.
2. Type in WhatsApp/Messages, Gmail (subject + body), Chrome address bar, a search box, a password field, a PIN field.
3. Check: first-keystroke suggestions after cold start (data load time), key latency, no dropped fast taps.
4. Autocorrect: `teh ` → `the `, Backspace restores `teh`; `dont ` → `don't `; `tambien ` while typing Spanish → `también `.
5. Long-press `e`, `n`, `?` → popup above the key, slide selects, release types; top row popup not clipped.
6. Backspace hold repeat/acceleration; emoji deletion; selection deletion; DEL in empty field removes chips.
7. Enter shows Send/Search/Next icons and performs the action; newline in multi-line fields.
8. Learning row: sentence translation appears for "I am going to call you tomorrow"; word hints readable at all hint sizes; dark mode.
9. Password field: no suggestions; afterwards no password words suggested anywhere.
10. Settings toggles apply live (number row, autocorrect, haptics, learning row, dark mode); "Delete learned words" works.
11. Rotate to landscape; check keyboard height is acceptable.
12. Upgrade test: install the previous build, type, install this build → learned words kept, context DB migrated.
13. Release build (signed in Android Studio) behaves like debug.
14. TalkBack: keys announce and type on double-tap.
