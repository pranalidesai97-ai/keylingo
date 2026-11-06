# KeyLingo — Known Issues and Prioritised Backlog

## Known issues / limitations (current code)

| # | Area | Issue | Impact |
|---|---|---|---|
| K1 | Verification | **Never run on a device or emulator.** IME lifecycle, Compose-in-IME rendering, gestures, haptics, latency and the R8 release build are unverified. | High until the TESTING.md §4 checklist passes |
| K2 | Build | This session's build used a locally assembled SDK (mirror `android.jar`, derived system-modules jar, stub build-tools). | Build in Android Studio with a real SDK for releases |
| K3 | Translation | Agreement assumes a masculine speaker/addressee (`estoy cansado`). | Wrong for many users — P1 |
| K4 | Translation | ~800 of the 2,500 most frequent English words still have no dictionary entry; any unknown content word hides the sentence translation (word hints still show). | Coverage — P1 |
| K5 | Translation | Not modelled: usted/ustedes register choice, vosotros, passive voice, relative clauses beyond simple `that/what`, possessive `'s`, comparatives beyond `more … than`, phrasal verbs beyond a small table. Indirect questions keep English word order (`no sé dónde él vive`). | Some sentences hidden or less natural |
| K6 | Lexicon | Only ~1,900 English words have curated frequency ranks; the rest are ordered by SCOWL tier then length (short rare words can rank too high). | Ranking quality for less common words |
| K7 | Lexicon | Spanish typing vocabulary is generated from the dictionary (8,447 forms); Spanish words outside it get no completion. | Spanish autocomplete coverage |
| K8 | CEFR | CEFR bands are derived from English frequency rank, not curated. | Advanced-mode hint selection is approximate |
| K9 | Input | Commit-based editing (no composing region/underline); autocorrect undo works only immediately after the correction. | UX polish |
| K10 | Layout | Fixed 92 + 260 dp height; not adapted for landscape/tablets/foldables. | Landscape usability |
| K11 | Layout | No globe/language key, swipe typing, space-bar cursor control, clipboard, emoji search, voice input. | Feature gaps vs. Gboard |
| K12 | Deps | AGP 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06 — 19 lint "newer version" warnings. | Maintenance |
| K13 | Timing | Bundled data loads asynchronously at keyboard start (≈150–270 ms desktop JVM, likely ~0.5–1.5 s on a phone); suggestions are empty until then. | First-keystroke experience |
| K14 | Tests | No Compose UI / instrumented tests. | UI regressions not caught automatically |

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

### P2 — Learning value
10. Tap a hint to expand (all forms, example) and save to a review list.
11. Spaced repetition of saved/seen words; learning statistics.
12. Formal register option (usted) and Spain/LatAm vocabulary option (`carro`/`coche`).
13. More idioms and phrasal verbs; relative clauses; possessive `'s`.
14. Broader Spanish lexicon (K7) and Spanish cold-start table.
15. CEFR curation (K8).

### P3 — Keyboard feature parity
16. Globe key / language toggle, space-bar cursor slide, clipboard strip, emoji search.
17. Swipe (gesture) typing.
18. Dependency upgrades (AGP/Kotlin 2.x/Compose BOM) with full regression run (K12).
