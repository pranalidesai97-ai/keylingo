# KeyLingo — Known Issues and Prioritised Backlog

## Resolved in the 2026-11-08 quality round (Stages 1–7)
English ranked by real frequency (was ~1,900 curated words, rest by dictionary tier) — K6. Spanish typing vocabulary 8.6k generated forms → 53.6k words from wordfreq — K7. Top-3,000 English translation coverage 59% → 81% — K4 (reduced). Spanish next-word table 21 → 89 heads. Spanish sound-alike misspellings understood. Negation no longer lost before an unknown Spanish verb. Companion-app screens share margins, card insets and alignment, and cap their width on tablets.

## Resolved in an earlier phase
Vibration was on by default; there was no sound toggle; backspace repeat relied on an untested gesture; the lexicon held 61.5k words; there was no grammar help, no Spanish→English sentence translation and no theme choice; learned words could not be reviewed or deleted individually; personal data could be included in Android backups.

## Known issues / limitations (current code)

| # | Area | Issue | Impact |
|---|---|---|---|
| K1 | Verification | **Never run on a device or emulator.** Unverified: IME lifecycle, Compose-in-IME rendering, gestures, vibration/sound feel, themes on real screens, latency, the R8 release build. JVM (Robolectric) renders and gesture tests exist but do not replace a device. | High until the TESTING.md §4 checklist passes |
| K2 | Build | This session's build used a locally assembled SDK (mirror `android.jar`, derived system-modules jar, stub build-tools). | Build in Android Studio with a real SDK for releases |
| K3 | Translation | Agreement assumes a masculine speaker/addressee (`estoy cansado`). | Wrong for many users — P1 |
| K4 | Translation | 81% of the 3,000 most frequent English words now have a dictionary entry (1,936 rows + 29 reverse-only), 57% of the top 5,000. Any unknown content word still hides the sentence translation in either direction; word hints still show. | Coverage — P1 |
| K5 | Translation | Not modelled: usted/ustedes register choice, vosotros, passive voice, relative clauses beyond simple `that/what`, possessive `'s`, comparatives beyond `more … than`, phrasal verbs beyond a small table. Indirect questions keep English word order (`no sé dónde él vive`). | Some sentences hidden or less natural |
| K6 | Lexicon | **Resolved:** English ranks come from wordfreq (CC BY-SA 4.0, see K30). Remaining: 32,863 words wordfreq lacks keep their SCOWL-tier order; wordfreq is case-folded, so lowercase forms of names were held back by a heuristic (Hunspell capitalised form + SCOWL tier ≥ 40 + a reviewed allow-list) that can miss some names or hold back a genuine word. | Minor ranking quirks |
| K7 | Lexicon | **Resolved:** 50,000 Spanish words from wordfreq (Hunspell-validated) plus dictionary forms. Words beyond the top 50k get no completion (still recognised if generated from the dictionary). | Rare Spanish words |
| K8 | CEFR | CEFR bands are derived from English frequency rank, not curated. Re-ranking by wordfreq moved 426 of 1,325 original entries by one band (e.g. kitchen A1→A2, fruit A2→B1), because written-text frequency differs from learner level. | Advanced-mode hint selection is approximate |
| K9 | Input | Commit-based editing (no composing region/underline); autocorrect undo works only immediately after the correction. | UX polish |
| K10 | Layout | Keys 260 dp; strip 86 dp (40 + 46) in assisted fields, 40 dp elsewhere. Not adapted for landscape/tablets/foldables. | Landscape usability |
| K11 | Layout | No globe/language key, swipe typing, space-bar cursor control, clipboard, emoji search, voice input. | Feature gaps vs. Gboard |
| K12 | Deps | AGP 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06 — 19 lint "newer version" warnings. | Maintenance |
| K13 | Timing | Bundled data loads asynchronously when the keyboard service is created: median 208 ms cold on the **desktop JVM** (5 runs, 182–267 ms). **Not measured on a phone**; expect several times slower. Starter phrases need no data; word suggestions are empty until the load finishes. | First-keystroke experience — measure on device |
| K14 | Tests | No instrumented tests. Compose UI is covered only by Robolectric (gestures, settings, PNG renders). | Device-only UI regressions not caught |
| K15 | Translation ES→EN | Rule-based. Third-person subjects stay ambiguous ("He/she ate a lot"). Present tense is kept literal ("¿Vienes conmigo?" → "Do you come with me?"). Not modelled: subordinate clauses beyond simple `cuando/que`, `se` passives/impersonals, relative clauses, most `de` phrases ("Es de madera"). Sentences it does not fully understand show only word glosses. | Some Spanish sentences get no chip |
| K16 | Grammar | Rule-based and deliberately narrow, preferring no flag to a false one. It does not detect general preposition errors, word order, run-on sentences, or tense agreement across clauses. English only. | Some errors go unflagged |
| K17 | Performance | Per-keystroke suggestion time on the **desktop JVM** (`SuggestionLatencyTest`): median ≈0.5 ms, p95 4.3 ms English / 6.2 ms Spanish, worst ≈21 ms. Runs off the main thread, debounced 20 ms, stale requests cancelled. **No phone measurement exists**; a mid-range phone is commonly 3–5× slower. | Measure on device (P0) |
| K18 | Backup | Backups are off for privacy: settings and learned words do not move to a new phone. | Owner decision (PROJECT_STATE §5) |
| K19 | Tests | JVM unit tests use desktop Java's regex engine, not Android's ICU engine. The ICU check (`tools/android-regex-check`) closes that gap for regex. Other platform differences (ART, Android-only APIs) are still only caught on a device. | Run the ICU check after the unit tests; keep device testing in the loop |
| K20 | Cross-app | The Chrome/YouTube fix is based on how those fields normally declare themselves (`textUri`, `textFilter`, SearchView, web inputs), not on EditorInfo captured from your phone. If a field still shows no translation, its inputType/imeOptions are needed (e.g. `adb shell dumpsys input_method`, look for `inputType=` and `imeOptions=`). | Confirm on device |
| K21 | Cross-app | A URL or e-mail anywhere in the current sentence turns off translation and grammar for that sentence. A missing space before a TLD-like word ("tired.it is late") also counts, so that sentence gets no chip. | Rare; no effect on typing |
| K23 | Themes | Gradients and colours are verified on JVM renders at phone size (411 dp at xxhdpi) and by measurement, not yet on the vivo screen. Panel calibration can make gradients look stronger or weaker. | Confirm on device |
| K24 | Phrases | The learning entries ("Basic", "English Course", "Learn English", "More" / "Curso básico", "Aprende español", "Más") are inserted as text like the other phrases. There are no course or category screens behind them. | Decide whether they should open something |
| K25 | Branding | The launcher icon is rasterised from the supplied 1254 px image (density PNGs up to 432 px). Its foreground is empty, so launchers that animate the foreground layer show no parallax. | Cosmetic |
| K26 | Language | In Auto mode, starter phrases follow the language last typed in this keyboard session (English after a restart). Explicit English or Spanish always shows that language. Settings has both "Typing language" and "Learning language": only Typing language changes the phrases. | Explain in onboarding if testers confuse them |
| K22 | Suggestions | In address bars autocorrect is off (unchanged). Distant lookalikes are now dropped and 2-letter typo words no longer lead the strip, but a correctly typed word can still be followed by close typo alternatives. | Cosmetic |
| K27 | Translation EN→ES | "free" is always *libre*: "the app is free" gives "está libre", where *gratis* (no cost) is meant. The rule-based translator has no price sense. | Misleading for prices |
| K28 | Translation EN→ES | "meet" is always *conocer* (meet for the first time). "We meet on Mondays" gives "Conocemos…" instead of *reunirse* / *quedar*. | Wrong sense for routine meetings |
| K29 | Translation EN→ES | A bare plural/mass noun after some verbs gets a definite article: "buy food" gives "comprar la comida" (should be "comprar comida"). | Unnatural, meaning mostly kept |
| K31 | Translation ES→EN | Unknown Spanish verbs are kept as typed, so sentences mix languages: "El perro no ladra" gives "The dog not ladra". The negation is kept (fixed in Stage 5), but the sentence is awkward, and hidden unless every word is understood. | Awkward glosses; chip usually hidden |
| K30 | Licensing | `es_words.tsv` and the rank column of `en_words.tsv` are adapted from wordfreq (CC BY-SA 4.0). Attribution, change list and the full licence text are bundled; ShareAlike applies to those files. **Needs legal review before public distribution:** adaptation/database-right classification, whether ShareAlike reaches anything beyond the two files, and whether an in-app licences screen is needed. Details: DATABASE_AND_DICTIONARY_INVENTORY.md §5. | Blocks public release until reviewed |

## Prioritised roadmap

### P0 — Verify on hardware
1. Run the TESTING.md §4 device checklist (debug and signed release) on at least one low/mid-range phone; capture latency (key → commit, key → suggestions) and memory.
2. Fix device findings (IME window/overlay clipping, gesture edge cases, R8 issues).
3. Build and sign the release in Android Studio with a real SDK.

### P1 — Quality of the core experience
4. **Speaker gender setting** (and optional addressee gender) for adjective/participle agreement (K3).
5. **Dictionary coverage**: the remaining 19% of the top 3,000 and the top 5,000 (K4); word senses for free/gratis and meet/reunirse (K27, K28).
6. **Licensing review** of the wordfreq-derived files before any public release (K30).
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
16. Spanish→English: verbs beyond the dictionary (K31).
17. CEFR curation (K8).

### P3 — Keyboard feature parity
18. Globe key / language toggle, space-bar cursor slide, clipboard strip, emoji search.
19. Swipe (gesture) typing.
20. Dependency upgrades (AGP/Kotlin 2.x/Compose BOM) with full regression run (K12).
