# KeyLingo — TODO / Roadmap

Priorities as requested: P0 Critical, P1 High, P2 Medium, P3 Nice to Have,
P4 Future Vision. Each item notes its source/status honestly — most of
this project's "verification" has been static analysis, not runtime
testing (see PROJECT_STATE.md's verification legend).

---

## P0 — Critical

- **Runtime-verify the dictionary seeding fix.** Build, install on a real
  device or emulator, type `"work"`, confirm a Spanish hint (`trabajar`)
  actually appears. Everything currently said about this fix working is
  ⚠ Assumed from static/simulated analysis — it has never actually run.
  This is the single most load-bearing unverified claim in the whole
  project right now.
- **Get real logcat evidence for the older, unresolved "total suggestion
  silence" report.** Tag `KLDebug`, 14 checkpoints, already instrumented
  and still present in the code (see DEBUG_STATE.md's last section). It's
  unknown whether this issue is the same as, related to, or separate from
  the dictionary bug that was just fixed. Don't assume it's resolved.

## P1 — High

- **Resolve the 11-word (8 practically-relevant) part-of-speech
  collision.** `dictionary_entries`' unique index doesn't include part of
  speech, so words like "love" (verb `amar` / noun `amor`) can only ever
  store one sense with the current schema + `OnConflictStrategy.REPLACE`.
  Needs an actual decision, not just a fix: e.g. add `partOfSpeech` to the
  unique index and adjust `DictionaryDao.findWord`/`RoomDictionaryProvider.lookup`
  to disambiguate by context, or pick one sense per word deliberately in
  the asset. Full word list in DEBUG_STATE.md.
- **Investigate typing responsiveness vs. Gboard** (product owner's
  Priority 2). Not started. Suggested angles, per the product owner's own
  brief: debounce timing (currently a flat 150ms in
  `scheduleSuggestionsRefresh`), recomposition counts in `KeyboardScreen`/`HintBar`,
  whether `refreshSuggestions()`'s single shared `currentFieldText()` read
  is itself expensive, coroutine dispatch overhead. Measure before
  changing anything — no responsiveness data exists yet.
- **Enrich the dictionary with `infinitive`/`gerund`/`rank`/`cefr` data.**
  The seeder and `DictionaryEntity` already fully support these columns;
  the bundled `en_es.json` just doesn't set them. This is content
  authoring, not a code change, and is the actual path toward the product
  owner's richer-hints goal (`trabajo`/`trabajar`/`trabajando` instead of
  a single translation).
- **Remove the temporary `KLDebug` logging** once the P0 items above are
  resolved and no longer needed — restores `KeyboardViewModel`'s original
  Android-framework-free design.

## P2 — Medium

- **Add a persistent/configurable number row.** Confirmed absent from
  `KeyboardLayoutProvider`'s letters-mode layout (numbers only exist under
  the symbols-mode switch). Product owner wants it "exactly like Gboard,"
  toggleable via Settings later — so this likely needs both a
  `KeyboardLayoutProvider` change and a new `KeyLingoSettings` field once
  built.
- **Review autocomplete ranking.** Current: pure `frequencyRank ASC` via
  `EnglishWordDao.findByPrefix`. Product owner wants the *architecture*
  prepared for frequency + recency + personalization, without
  overengineering yet — e.g. confirm `AutocompleteSuggestion`/`AutocompleteProvider`
  can accommodate a future recency/personalization signal without another
  interface break, but don't build the signal itself yet.
- **Seeding race on cold start.** `KeyboardViewModel`'s settings-flow
  collector can trigger a refresh before the seeding coroutine finishes.
  Pre-existing, low-observed-impact (limited to the very first refresh
  after a fresh install), not yet addressed.
- **Silent seeding failure path.** If asset reading throws `IOException`,
  it's logged and returns with no retry, no user-visible signal. Not
  confirmed to have occurred; worth hardening regardless.

## P3 — Nice to Have

- General keyboard feel polish: backspace hold-to-repeat-with-acceleration,
  long-press accents, additional animations beyond the current
  suggestion-bar fade/slide, haptics.
- Autocomplete word list expansion beyond 772 (architecture already
  supports a drop-in larger file with zero code changes).
- Dictionary expansion beyond 734 (same zero-code-change path).

## P4 — Future Vision

Per the product owner's stated long-term direction — none of this is
started; listed here for roadmap continuity only:

- Context-aware, sentence-level Spanish suggestions (not just per-word
  translation) — e.g. typing "I am having pizza now" progressively
  surfacing `yo` / `estoy` / `comiendo` / `pizza` / `ahora`.
- Grammar correction.
- Verb conjugation teaching (ties into the P1 dictionary-enrichment item
  above, but goes further — actual conjugation tables/practice, not just
  storing a few forms).
- Gender, articles, prepositions as explicit teaching content, not just
  incidental translation.
- Useful phrases / idioms beyond the current 35 phrase chunks.
- Pronunciation support.
- Vocabulary mastery tracking, confidence scoring, word familiarity.
- Spaced repetition, daily revision/review.
- Adaptive difficulty (beginner → advanced transition based on
  demonstrated mastery, not just a manual `LearningMode` setting).
- Personalized learning profile / personalized vocabulary.
- AI-generated sentences and AI explanations.
- Learning statistics, achievements.
- Cloud sync (currently fully offline by design).
- Additional language packs beyond English→Spanish — architecture
  (`LanguagePack`/`SourceLanguageRules` split) is already built to support
  this without a rewrite; adding one is "write a new `LanguagePack`
  implementation and asset," not an architecture change.

The product owner's explicit goal for all of the above: "the keyboard
should feel invisible while teaching... learning should happen naturally
without interrupting typing" — every future feature here should be
weighed against that, not added purely for feature-completeness.
