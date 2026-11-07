# KeyLingo — Known Issues and Prioritised Backlog

## Resolved in this phase
Vibration was on by default; there was no sound toggle; backspace repeat relied on an untested gesture; the lexicon held 61.5k words; there was no grammar help, no Spanish→English sentence translation and no theme choice; learned words could not be reviewed or deleted individually; personal data could be included in Android backups.

## Known issues / limitations (current code)

| # | Area | Issue | Impact |
|---|---|---|---|
| K1 | Verification | **Never run on a device or emulator.** Unverified: IME lifecycle, Compose-in-IME rendering, gestures, vibration/sound feel, themes on real screens, latency, the R8 release build. JVM (Robolectric) renders and gesture tests exist but do not replace a device. | High until the TESTING.md §4 checklist passes |
| K2 | Build | This session's build used a locally assembled SDK (mirror `android.jar`, derived system-modules jar, stub build-tools). | Build in Android Studio with a real SDK for releases |
| K3 | Translation | Agreement assumes a masculine speaker/addressee (`estoy cansado`). | Wrong for many users — P1 |
| K4 | Translation | ~800 of the 2,500 most frequent English words still have no dictionary entry (1,325 lemmas + 27 reverse-only Spanish words). Any unknown content word hides the sentence translation in either direction; word hints still show. | Coverage — P1 |
| K5 | Translation | Not modelled: usted/ustedes register choice, vosotros, passive voice, relative clauses beyond simple `that/what`, possessive `'s`, comparatives beyond `more … than`, phrasal verbs beyond a small table. Indirect questions keep English word order (`no sé dónde él vive`). | Some sentences hidden or less natural |
| K6 | Lexicon | Only ~1,900 English words have curated frequency ranks. The other ~110k are ordered by SCOWL tier then length, so short rare words can rank too high. Owner decision needed on a frequency corpus with clear redistribution terms. | Ranking quality for less common words |
| K7 | Lexicon | Spanish typing vocabulary is generated from the dictionary (8,447 forms); Spanish words outside it get no completion. | Spanish autocomplete coverage |
| K8 | CEFR | CEFR bands are derived from English frequency rank, not curated. | Advanced-mode hint selection is approximate |
| K9 | Input | Commit-based editing (no composing region/underline); autocorrect undo works only immediately after the correction. | UX polish |
| K10 | Layout | Keys 260 dp; strip 86 dp (40 + 46) in assisted fields, 40 dp elsewhere. Not adapted for landscape/tablets/foldables. | Landscape usability |
| K11 | Layout | No globe/language key, swipe typing, space-bar cursor control, clipboard, emoji search, voice input. | Feature gaps vs. Gboard |
| K12 | Deps | AGP 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06 — 19 lint "newer version" warnings. | Maintenance |
| K13 | Timing | Bundled data loads asynchronously at keyboard start: ≈0.4–0.8 s on the desktop JVM with the 111.8k lexicon, likely 1–3 s on a phone. Suggestions are empty until then. | First-keystroke experience |
| K14 | Tests | No instrumented tests. Compose UI is covered only by Robolectric (gestures, settings, PNG renders). | Device-only UI regressions not caught |
| K15 | Translation ES→EN | Rule-based. Third-person subjects stay ambiguous ("He/she ate a lot"). Present tense is kept literal ("¿Vienes conmigo?" → "Do you come with me?"). Not modelled: subordinate clauses beyond simple `cuando/que`, `se` passives/impersonals, relative clauses, most `de` phrases ("Es de madera"). Sentences it does not fully understand show only word glosses. | Some Spanish sentences get no chip |
| K16 | Grammar | Rule-based and deliberately narrow, preferring no flag to a false one. It does not detect general preposition errors, word order, run-on sentences, or tense agreement across clauses. English only. | Some errors go unflagged |
| K17 | Performance | The typo lookup (6–10 ms per call on the build JVM, budget 15 ms) is the slowest per-keystroke step. It runs off the main thread, debounced, but may approach 30–50 ms on a low-end phone. | Measure on device (P0) |
| K18 | Backup | Backups are off for privacy: settings and learned words do not move to a new phone. | Owner decision (PROJECT_STATE §5) |
| K19 | Tests | JVM unit tests use desktop Java's regex engine, not Android's ICU engine. The ICU check (`tools/android-regex-check`) closes that gap for regex. Other platform differences (ART, Android-only APIs) are still only caught on a device. | Run the ICU check after the unit tests; keep device testing in the loop |
| K20 | Cross-app | The Chrome/YouTube fix is based on how those fields normally declare themselves (`textUri`, `textFilter`, SearchView, web inputs), not on EditorInfo captured from your phone. If a field still shows no translation, its inputType/imeOptions are needed (e.g. `adb shell dumpsys input_method`, look for `inputType=` and `imeOptions=`). | Confirm on device |
| K21 | Cross-app | A URL or e-mail anywhere in the current sentence turns off translation and grammar for that sentence. A missing space before a TLD-like word ("tired.it is late") also counts, so that sentence gets no chip. | Rare; no effect on typing |
| K23 | Themes | Ocean Mist stays flat, because the approved design was kept exactly. The other four are gradients. Dark variants use subtler gradients so text stays readable. | Owner can ask for a gradient Ocean Mist |
| K24 | Phrases | The learning entries ("Basic", "English Course", "Learn English", "More" / "Curso básico", "Aprende español", "Más") are inserted as text like the other phrases. There are no course or category screens behind them. | Decide whether they should open something |
| K22 | Suggestions | In address bars autocorrect is off (unchanged), and a correctly typed word can be followed by weak typo alternatives ("today" → foray, toady). Pre-existing ranking, not changed here. | Cosmetic |

## Prioritised roadmap

### P0 — Verify on hardware
1. Run the TESTING.md §4 device checklist (debug and signed release) on at least one low/mid-range phone; capture latency (key → commit, key → suggestions) and memory.
2. Fix device findings (IME window/overlay clipping, gesture edge cases, R8 issues).
3. Build and sign the release in Android Studio with a real SDK.

### P1 — Quality of the core experience
4. **Speaker gender setting** (and optional addressee gender) for adjective/participle agreement (K3).
5. **Dictionary coverage**: add the missing top-3,000 lemmas (K4); keep `BundledDataIntegrityTest` green.
6. **Frequency data** for the full lexicon from a corpus with clear redistribution terms (K6) — decision needed on licensing (SUBTLEX/wordfreq terms were not accepted in this pass).
7. **Composing-text model** (`setComposingText`) for underline, reliable undo and editing in the middle of words (K9).
8. Landscape/tablet height and one-handed mode (K10).
9. Instrumented Compose UI tests for keys, popup and strip (K14).
10. ES→EN: `se` constructions, relative clauses, `de` complements, progressive reading of present motion verbs (K15).
11. Grammar: more rules with a false-positive corpus test (K16).

### P2 — Learning value
12. Tap a hint to expand (all forms, example) and save to a review list.
13. Spaced repetition of saved/seen words; learning statistics.
14. Formal register option (usted) and Spain/LatAm vocabulary option (`carro`/`coche`).
15. More idioms and phrasal verbs; relative clauses; possessive `'s`.
16. Broader Spanish lexicon (K7) and Spanish cold-start table.
17. CEFR curation (K8).

### P3 — Keyboard feature parity
18. Globe key / language toggle, space-bar cursor slide, clipboard strip, emoji search.
19. Swipe (gesture) typing.
20. Dependency upgrades (AGP/Kotlin 2.x/Compose BOM) with full regression run (K12).
